/*
 EcmDroid - Android Diagnostic Tool for Buell Motorcycles
 Copyright (C) 2012 by Michel Marti

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 3
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, see <http://www.gnu.org/licenses/>.
 */
package biz.logicminds.buelltune.fragments

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import biz.logicminds.buelltune.AppContainer
import biz.logicminds.buelltune.AppPreferences
import biz.logicminds.buelltune.R
import biz.logicminds.buelltune.activities.LlmSettingsActivity
import biz.logicminds.buelltune.activities.MainActivity
import biz.logicminds.buelltune.chat.ChatMessageEntity
import biz.logicminds.buelltune.chat.ChatRepository
import biz.logicminds.buelltune.chat.ConversationEntity
import biz.logicminds.buelltune.chat.EcmTools
import biz.logicminds.buelltune.chat.ProviderId
import biz.logicminds.buelltune.chat.Role
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The Chat drawer tab (R14, R15, R18, R19): setup prompt when no provider is
 * configured, otherwise a rider-browsable conversation list and, inside an
 * open conversation, a message list/input/send-button/tool-call-indicator
 * view. Hosted by [MainActivity.switchToFragment]'s `nav_chat` case through
 * `getSupportFragmentManager()` (KTD4/KTD9) - this class extends
 * `androidx.fragment.app.Fragment`, never the deprecated `android.app.Fragment`
 * every other drawer screen uses.
 *
 * Never talks to [EcmTools], a Koog `PromptExecutor`, or any LLM provider
 * directly - every request passes through [ChatRepository], resolved from
 * [AppContainer], the one seam that enforces KTD7's replay rule and
 * persists both sides of every turn.
 */
class ChatFragment : Fragment() {
    private val TAG = "ChatFragment"

    private lateinit var chatRepository: ChatRepository
    private lateinit var ecmTools: EcmTools

    private lateinit var setupContainer: View
    private lateinit var listContainer: View
    private lateinit var conversationContainer: View

    private lateinit var openSettingsButton: Button
    private lateinit var newConversationButton: Button
    private lateinit var noConversationsMessage: TextView
    private lateinit var conversationList: RecyclerView

    private lateinit var backButton: Button
    private lateinit var conversationTitle: TextView
    private lateinit var messageList: RecyclerView
    private lateinit var toolCallStatus: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button

    private lateinit var conversationAdapter: ConversationAdapter
    private lateinit var messageAdapter: ChatMessageAdapter

    private var currentConversation: ConversationEntity? = null
    private var messagesJob: Job? = null
    private var sendJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.chat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appContainer = AppContainer.from(requireContext())
        chatRepository = appContainer.chatRepository
        ecmTools = appContainer.ecmTools

        bindViews(view)

        conversationAdapter = ConversationAdapter(
            onOpen = { conversation -> showConversation(conversation) },
            onDelete = { conversation -> confirmDeleteConversation(conversation) },
        )
        conversationList.layoutManager = LinearLayoutManager(requireContext())
        conversationList.adapter = conversationAdapter

        messageAdapter = ChatMessageAdapter(onSuggestionTapped = { screenId -> navigateToSuggestion(screenId) })
        messageList.layoutManager = LinearLayoutManager(requireContext())
        messageList.adapter = messageAdapter

        wireListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            chatRepository.conversations.collectLatest { conversations ->
                conversationAdapter.submitList(conversations)
                noConversationsMessage.visibility = if (conversations.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        if (AppPreferences.configuredProviders(requireContext()).isEmpty()) {
            showSetup()
        } else {
            showConversationList()
        }
    }

    private fun bindViews(view: View) {
        setupContainer = view.findViewById(R.id.setupContainer)
        listContainer = view.findViewById(R.id.listContainer)
        conversationContainer = view.findViewById(R.id.conversationContainer)

        openSettingsButton = view.findViewById(R.id.openSettingsButton)
        newConversationButton = view.findViewById(R.id.newConversationButton)
        noConversationsMessage = view.findViewById(R.id.noConversationsMessage)
        conversationList = view.findViewById(R.id.conversationList)

        backButton = view.findViewById(R.id.backButton)
        conversationTitle = view.findViewById(R.id.conversationTitle)
        messageList = view.findViewById(R.id.messageList)
        toolCallStatus = view.findViewById(R.id.toolCallStatus)
        messageInput = view.findViewById(R.id.messageInput)
        sendButton = view.findViewById(R.id.sendButton)
    }

    private fun wireListeners() {
        openSettingsButton.setOnClickListener {
            startActivity(Intent(requireContext(), LlmSettingsActivity::class.java))
        }
        newConversationButton.setOnClickListener { showNewConversationPicker() }
        backButton.setOnClickListener { showConversationList() }
        sendButton.setOnClickListener { sendCurrentMessage() }
    }

    // --- State transitions (R14, R15) ---

    private fun showSetup() {
        currentConversation = null
        setupContainer.visibility = View.VISIBLE
        listContainer.visibility = View.GONE
        conversationContainer.visibility = View.GONE
    }

    private fun showConversationList() {
        currentConversation = null
        messagesJob?.cancel()
        setupContainer.visibility = View.GONE
        listContainer.visibility = View.VISIBLE
        conversationContainer.visibility = View.GONE
    }

    private fun showConversation(conversation: ConversationEntity) {
        currentConversation = conversation
        setupContainer.visibility = View.GONE
        listContainer.visibility = View.GONE
        conversationContainer.visibility = View.VISIBLE
        conversationTitle.text = conversation.title
        observeMessages(conversation.id)
    }

    private fun observeMessages(conversationId: Long) {
        messagesJob?.cancel()
        messagesJob = viewLifecycleOwner.lifecycleScope.launch {
            chatRepository.messages(conversationId).collectLatest { messages ->
                messageAdapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    messageList.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    // --- New conversation (R10, R15) ---

    private fun showNewConversationPicker() {
        val providers = AppPreferences.configuredProviders(requireContext())
        if (providers.isEmpty()) {
            Toast.makeText(requireContext(), R.string.chat_no_providers_configured, Toast.LENGTH_LONG).show()
            return
        }
        val labels = providers.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.chat_choose_provider_title)
            .setItems(labels) { _, index -> createConversation(providers[index]) }
            .show()
    }

    private fun createConversation(providerId: ProviderId) {
        viewLifecycleOwner.lifecycleScope.launch {
            // "default" is the persisted modelId column (KD5) - ChatAgentFactory
            // resolves the real Koog LLModel from providerId/credentials alone
            // today, so no richer value is actually read back out of this column.
            val title = getString(
                R.string.chat_default_conversation_title,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date()),
            )
            val conversationId = chatRepository.createConversation(providerId, "default", title)
            val conversation = chatRepository.conversations.first().first { it.id == conversationId }
            showConversation(conversation)
        }
    }

    private fun confirmDeleteConversation(conversation: ConversationEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.chat_delete_conversation_title)
            .setMessage(getString(R.string.chat_delete_conversation_message, conversation.title))
            .setPositiveButton(R.string.chat_delete_conversation) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    chatRepository.deleteConversation(conversation.id)
                    if (currentConversation?.id == conversation.id) {
                        showConversationList()
                    }
                }
            }
            .setNegativeButton(R.string.chat_cancel, null)
            .show()
    }

    // --- Sending a turn (R12, R19) ---

    private fun sendCurrentMessage() {
        val conversation = currentConversation ?: return
        val text = messageInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty() || sendJob?.isActive == true) {
            return
        }
        messageInput.setText("")

        val providerId = ProviderId.valueOf(conversation.providerId)
        val credentials = AppPreferences.credentialsFor(requireContext(), providerId)

        sendButton.isEnabled = false
        toolCallStatus.visibility = View.VISIBLE
        toolCallStatus.text = null

        sendJob = viewLifecycleOwner.lifecycleScope.launch {
            val statusJob = launch {
                chatRepository.toolCallEvents.collect { toolName ->
                    toolCallStatus.text = getString(R.string.chat_tool_call_status, toolName)
                }
            }
            try {
                chatRepository.sendMessage(conversation.id, text, ecmTools, credentials)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "sendMessage failed for conversation ${conversation.id}", e)
                Toast.makeText(requireContext(), e.message ?: getString(R.string.chat_send), Toast.LENGTH_LONG).show()
            } finally {
                statusJob.cancel()
                toolCallStatus.visibility = View.GONE
                sendButton.isEnabled = true
            }
        }
    }

    // --- Suggestion cards (R2, KTD8) ---

    // DiscouragedApi: screenId is an LLM-produced string (KTD8's [[SUGGEST:...]]
    // marker), never known at compile time, so a static R.id reference is not
    // possible - resolving it by name against main_drawer.xml's ids is the only
    // way to validate/deep-link it (SuggestionCard.kt's KDoc assigns this
    // validation to this unit). Mirrored, identically justified, in
    // ChatMessageAdapter.ViewHolder.bind below.
    @Suppress("DiscouragedApi")
    private fun navigateToSuggestion(screenId: String) {
        val resolvedId = resources.getIdentifier(screenId, "id", requireContext().packageName)
        if (resolvedId == 0) {
            Log.w(TAG, "Suggestion card screenId \"$screenId\" does not match any drawer item; ignoring tap.")
            return
        }
        (requireActivity() as MainActivity).navigateToDrawerItem(resolvedId)
    }

    /** [ConversationEntity] rows for [listContainer] (R15). */
    private class ConversationAdapter(
        private val onOpen: (ConversationEntity) -> Unit,
        private val onDelete: (ConversationEntity) -> Unit,
    ) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

        private var items: List<ConversationEntity> = emptyList()

        // NotifyDataSetChanged: conversation lists are small (rider-created,
        // not paginated) and Room's Flow already re-delivers the full list on
        // every write - a full-list DiffUtil pass would cost more than it
        // saves here. Mirrored, identically justified, in
        // ChatMessageAdapter.submitList below.
        @Suppress("NotifyDataSetChanged")
        fun submitList(list: List<ConversationEntity>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_conversation_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], onOpen, onDelete)
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val title: TextView = view.findViewById(R.id.conversationItemTitle)
            private val subtitle: TextView = view.findViewById(R.id.conversationItemSubtitle)
            private val delete: Button = view.findViewById(R.id.conversationItemDelete)

            fun bind(conversation: ConversationEntity, onOpen: (ConversationEntity) -> Unit, onDelete: (ConversationEntity) -> Unit) {
                title.text = conversation.title
                subtitle.text = conversation.providerId
                itemView.setOnClickListener { onOpen(conversation) }
                delete.setOnClickListener { onDelete(conversation) }
            }
        }
    }

    /** [ChatMessageEntity] rows for [conversationContainer]'s transcript (R19). */
    private class ChatMessageAdapter(
        private val onSuggestionTapped: (String) -> Unit,
    ) : RecyclerView.Adapter<ChatMessageAdapter.ViewHolder>() {

        private var items: List<ChatMessageEntity> = emptyList()

        @Suppress("NotifyDataSetChanged")
        fun submitList(list: List<ChatMessageEntity>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_message_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], onSuggestionTapped)
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val bubble: LinearLayout = view.findViewById(R.id.messageBubble)
            private val text: TextView = view.findViewById(R.id.messageText)
            private val suggestion: TextView = view.findViewById(R.id.suggestionCard)

            /**
             * Single row layout, left/right alignment and background toggled
             * by [ChatMessageEntity.role] rather than two separate item
             * layouts - a rendered [biz.logicminds.buelltune.chat.SuggestionCard]
             * (if [ChatMessageEntity.suggestionScreenId]/[ChatMessageEntity.suggestionLabel]
             * are set) is only ever tappable when its `screenId` resolves to a
             * real `main_drawer.xml` id - an unrecognized one renders as a
             * plain, non-clickable label rather than crashing or navigating.
             * `getIdentifier` (DiscouragedApi, suppressed) is required for the
             * same reason as [ChatFragment.navigateToSuggestion]: `screenId` is
             * an LLM-produced string, never a compile-time-known `R.id`.
             */
            @Suppress("DiscouragedApi")
            fun bind(message: ChatMessageEntity, onSuggestionTapped: (String) -> Unit) {
                text.text = message.content

                val isUser = message.role == Role.USER.name
                val density = bubble.resources.displayMetrics.density
                val nearMargin = (8 * density).toInt()
                val farMargin = (48 * density).toInt()
                val params = bubble.layoutParams as FrameLayout.LayoutParams
                params.gravity = if (isUser) Gravity.END else Gravity.START
                params.marginStart = if (isUser) farMargin else nearMargin
                params.marginEnd = if (isUser) nearMargin else farMargin
                bubble.layoutParams = params
                bubble.setBackgroundResource(if (isUser) R.drawable.chat_bubble_user else R.drawable.chat_bubble_assistant)

                val screenId = message.suggestionScreenId
                val label = message.suggestionLabel
                if (screenId != null && label != null) {
                    suggestion.visibility = View.VISIBLE
                    suggestion.text = label
                    val resolvedId = suggestion.resources.getIdentifier(screenId, "id", suggestion.context.packageName)
                    if (resolvedId != 0) {
                        suggestion.isClickable = true
                        suggestion.setOnClickListener { onSuggestionTapped(screenId) }
                    } else {
                        suggestion.isClickable = false
                        suggestion.setOnClickListener(null)
                    }
                } else {
                    suggestion.visibility = View.GONE
                    suggestion.setOnClickListener(null)
                }
            }
        }
    }
}
