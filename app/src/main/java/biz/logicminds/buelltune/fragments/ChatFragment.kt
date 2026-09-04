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
import biz.logicminds.buelltune.chat.ConversationWithPreview
import biz.logicminds.buelltune.chat.EcmTools
import biz.logicminds.buelltune.chat.ProviderId
import biz.logicminds.buelltune.chat.Role
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
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

    private lateinit var chatRepository: ChatRepository
    private lateinit var ecmTools: EcmTools

    private lateinit var setupContainer: View
    private lateinit var listContainer: View
    private lateinit var conversationContainer: View

    private lateinit var openSettingsButton: Button
    private lateinit var newConversationButton: Button
    private lateinit var noConversationsMessage: TextView
    private lateinit var conversationList: RecyclerView
    private lateinit var promptChips: ChipGroup

    private lateinit var backButton: Button
    private lateinit var conversationTitle: TextView
    private lateinit var messageList: RecyclerView
    private lateinit var thinkingIndicator: View
    private lateinit var toolCallStatus: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button

    private lateinit var conversationAdapter: ConversationAdapter
    private lateinit var messageAdapter: ChatMessageAdapter

    private var currentConversation: ConversationEntity? = null
    private var messagesJob: Job? = null
    private var sendJob: Job? = null

    /**
     * R20's five predefined prompt chips: [chatChipLabel] is the short chip
     * text, [chatChipPrompt] the full template question sent as the chip's
     * conversation's first turn (see [startConversationFromChip]).
     */
    private data class PromptChipDefinition(val chatChipLabel: Int, val chatChipPrompt: Int)

    private val promptChipDefinitions = listOf(
        PromptChipDefinition(R.string.chat_chip_health_check_label, R.string.chat_chip_health_check_prompt),
        PromptChipDefinition(R.string.chat_chip_tps_zero_label, R.string.chat_chip_tps_zero_prompt),
        PromptChipDefinition(R.string.chat_chip_afv_label, R.string.chat_chip_afv_prompt),
        PromptChipDefinition(R.string.chat_chip_error_codes_label, R.string.chat_chip_error_codes_prompt),
        PromptChipDefinition(R.string.chat_chip_pre_ride_label, R.string.chat_chip_pre_ride_prompt),
    )

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
        populatePromptChips()

        viewLifecycleOwner.lifecycleScope.launch {
            chatRepository.conversationPreviews.collectLatest { conversations ->
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

    /**
     * buelltune-kjh: [showSetup]/[showConversationList] were previously
     * chosen once, in `onViewCreated`, from [AppPreferences.configuredProviders]
     * at that single point in time. The setup prompt's own "Configure AI
     * Provider" button (see [wireListeners]) launches [LlmSettingsActivity]
     * on top of this fragment - which stays alive, still showing
     * [setupContainer], the whole time. Returning from a key just saved
     * there re-enters here via `onResume`, not `onViewCreated`, so without
     * this re-check the rider was stuck looking at a stale "configure a
     * provider" prompt despite a provider now genuinely being configured.
     * Scoped to only fire while [setupContainer] is the visible screen: an
     * already-open conversation list/transcript must never be yanked back
     * to setup by an unrelated resume.
     */
    override fun onResume() {
        super.onResume()
        val activity = requireActivity() as MainActivity
        activity.setTitle(getString(R.string.chat_title))
        activity.updateConnectButton()
        if (setupContainer.visibility == View.VISIBLE && AppPreferences.configuredProviders(requireContext()).isNotEmpty()) {
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
        promptChips = view.findViewById(R.id.promptChips)

        backButton = view.findViewById(R.id.backButton)
        conversationTitle = view.findViewById(R.id.conversationTitle)
        messageList = view.findViewById(R.id.messageList)
        thinkingIndicator = view.findViewById(R.id.thinkingIndicator)
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

    private fun showNewConversationPicker(onSelected: (ProviderId, String) -> Unit = ::createConversation) {
        val providers = AppPreferences.configuredProviders(requireContext())
        if (providers.isEmpty()) {
            Toast.makeText(requireContext(), R.string.chat_no_providers_configured, Toast.LENGTH_LONG).show()
            return
        }
        val labels = providers.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.chat_choose_provider_title)
            .setItems(labels) { _, index -> pickModelThen(providers[index], onSelected) }
            .show()
    }

    /**
     * Queries [providerId]'s live model list (R21, a real network call via
     * [ChatRepository.listModels]) and lets the rider pick one; matches
     * [showNewConversationPicker]'s "skip the dialog when there's no real
     * choice" rule by auto-selecting when the query returns exactly one
     * candidate. A failure (offline, a since-revoked key, a provider
     * outage) or an empty result surfaces as a Toast and aborts -
     * conversation creation never silently falls back to a guessed model.
     */
    private fun pickModelThen(providerId: ProviderId, onSelected: (ProviderId, String) -> Unit) {
        val credentials = AppPreferences.credentialsFor(requireContext(), providerId)
        val progress = AlertDialog.Builder(requireContext())
            .setTitle(R.string.chat_loading_models_title)
            .setCancelable(false)
            .show()
        viewLifecycleOwner.lifecycleScope.launch {
            val models = try {
                chatRepository.listModels(providerId, credentials)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to list models for $providerId", e)
                emptyList()
            }
            progress.dismiss()
            when {
                models.isEmpty() -> Toast.makeText(
                    requireContext(),
                    getString(R.string.chat_no_models_found, providerId.name),
                    Toast.LENGTH_LONG,
                ).show()
                models.size == 1 -> onSelected(providerId, models.single().id)
                else -> {
                    val labels = models.map { it.id }.toTypedArray()
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.chat_choose_model_title)
                        .setItems(labels) { _, index -> onSelected(providerId, models[index].id) }
                        .show()
                }
            }
        }
    }

    private fun createConversation(providerId: ProviderId, modelId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            showConversation(createConversationEntity(providerId, modelId))
        }
    }

    private suspend fun createConversationEntity(providerId: ProviderId, modelId: String): ConversationEntity {
        val title = getString(
            R.string.chat_default_conversation_title,
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date()),
        )
        val conversationId = chatRepository.createConversation(providerId, modelId, title)
        return chatRepository.conversations.first().first { it.id == conversationId }
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

    // --- Prompt chips (R20) ---

    /**
     * Inflates [promptChipDefinitions] into [promptChips] once at view
     * creation. Reveals the (parent-hidden-by-default) `ChipGroup` itself -
     * actual on-screen visibility still tracks [listContainer]'s own
     * visibility, so nothing shows while a conversation is open or setup is
     * pending.
     */
    private fun populatePromptChips() {
        promptChips.removeAllViews()
        // The app's base theme (AppTheme -> Theme.AppCompat.Light.DarkActionBar,
        // res/values/styles.xml) is not a Theme.MaterialComponents descendant.
        // Chip's constructor hard-fails with IllegalArgumentException against a
        // plain AppCompat theme (ThemeEnforcement.checkMaterialTheme) - verified
        // by an actual crash during manual smoke testing against ecmsimRun, not
        // a defensive guess. A ThemeOverlay.MaterialComponents.* wrapper does
        // NOT fix this: only a full Theme.MaterialComponents.* theme sets the
        // isMaterialTheme=true flag ThemeEnforcement checks for (confirmed by
        // inspecting the material library's own theme definitions - overlays
        // don't set it, "Bridge" themes do) - this crashed identically even
        // after wrapping with ThemeOverlay.MaterialComponents.Light, caught by
        // a second smoke-test pass, not assumed fixed after the first attempt.
        // Theme.MaterialComponents.Light.DarkActionBar.Bridge is the material
        // library's own purpose-built theme for exactly this case: it extends
        // Theme.AppCompat.Light.DarkActionBar (the app's real base theme) while
        // still setting isMaterialTheme=true, so wrapping just this context
        // satisfies Chip's requirement without changing the app-wide AppCompat
        // theme every other screen relies on (§14/AGENTS.md - avoid unrelated
        // changes to existing screens).
        val chipContext = android.view.ContextThemeWrapper(
            requireContext(),
            com.google.android.material.R.style.Theme_MaterialComponents_Light_DarkActionBar_Bridge,
        )
        for (definition in promptChipDefinitions) {
            val chip = Chip(chipContext)
            chip.text = getString(definition.chatChipLabel)
            chip.isClickable = true
            chip.isCheckable = false
            chip.setOnClickListener { startConversationFromChip(getString(definition.chatChipPrompt)) }
            promptChips.addView(chip)
        }
        promptChips.visibility = View.VISIBLE
    }

    /**
     * Tapping a chip picks a provider, then a model, and starts a
     * brand-new conversation (R20) - unlike [newConversationButton]'s
     * picker (R10), which always lets the rider confirm/choose the
     * provider, a chip is a one-tap shortcut for that first step only:
     * with exactly one provider configured it is used directly, only
     * falling back to [showNewConversationPicker]'s AlertDialog when
     * there is a real provider choice to make. [pickModelThen] (R21) is
     * never skipped on this path - only auto-resolved when the queried
     * provider itself has just one usable model.
     */
    private fun startConversationFromChip(templateText: String) {
        val providers = AppPreferences.configuredProviders(requireContext())
        if (providers.isEmpty()) {
            Toast.makeText(requireContext(), R.string.chat_no_providers_configured, Toast.LENGTH_LONG).show()
            return
        }
        val onSelected: (ProviderId, String) -> Unit = { providerId, modelId -> createConversationAndSend(providerId, modelId, templateText) }
        if (providers.size == 1) {
            pickModelThen(providers.first(), onSelected)
        } else {
            showNewConversationPicker(onSelected)
        }
    }

    private fun createConversationAndSend(providerId: ProviderId, modelId: String, text: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val conversation = createConversationEntity(providerId, modelId)
            showConversation(conversation)
            sendText(conversation, text)
        }
    }

    // --- Sending a turn (R12, R19) ---

    private fun sendCurrentMessage() {
        val conversation = currentConversation ?: return
        val text = messageInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty() || sendJob?.isActive == true) {
            return
        }
        messageInput.setText("")
        sendText(conversation, text)
    }

    private fun sendText(conversation: ConversationEntity, text: String) {
        val providerId = ProviderId.valueOf(conversation.providerId)
        val credentials = AppPreferences.credentialsFor(requireContext(), providerId)

        sendButton.isEnabled = false
        thinkingIndicator.visibility = View.VISIBLE
        toolCallStatus.text = getString(R.string.chat_thinking)

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
                thinkingIndicator.visibility = View.GONE
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

    /** [ConversationWithPreview] rows for [listContainer] (R15, buelltune-1vt). */
    private class ConversationAdapter(
        private val onOpen: (ConversationEntity) -> Unit,
        private val onDelete: (ConversationEntity) -> Unit,
    ) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

        private var items: List<ConversationWithPreview> = emptyList()

        // NotifyDataSetChanged: conversation lists are small (rider-created,
        // not paginated) and Room's Flow already re-delivers the full list on
        // every write - a full-list DiffUtil pass would cost more than it
        // saves here. Mirrored, identically justified, in
        // ChatMessageAdapter.submitList below.
        @Suppress("NotifyDataSetChanged")
        fun submitList(list: List<ConversationWithPreview>) {
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

            /**
             * buelltune-1vt: the row's headline is the first user message
             * (truncated), not [ConversationEntity.title] - that field is
             * only ever a creation-time date/time label (see
             * [ChatFragment.createConversationEntity]), unhelpful for
             * telling two past conversations apart at a glance. A brand-new
             * conversation with no turns yet (no [ConversationWithPreview.preview])
             * falls back to it. The date moves to the subtitle, alongside
             * provider/model.
             */
            fun bind(item: ConversationWithPreview, onOpen: (ConversationEntity) -> Unit, onDelete: (ConversationEntity) -> Unit) {
                val conversation = item.conversation
                val preview = item.preview?.trim()?.takeIf { it.isNotEmpty() }
                title.text = preview?.let(::truncatePreview) ?: conversation.title

                val modelLabel = if (conversation.modelId.isBlank() || conversation.modelId == "default") {
                    conversation.providerId
                } else {
                    "${conversation.providerId} \u00b7 ${conversation.modelId}"
                }
                val dateLabel = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(conversation.createdAt))
                subtitle.text = "$dateLabel \u00b7 $modelLabel"

                itemView.setOnClickListener { onOpen(conversation) }
                delete.setOnClickListener { onDelete(conversation) }
            }
        }
    }

    /** [ChatMessageEntity] rows for [conversationContainer]'s transcript (R19, buelltune-7ls). */
    private class ChatMessageAdapter(
        private val onSuggestionTapped: (String) -> Unit,
    ) : RecyclerView.Adapter<ChatMessageAdapter.ViewHolder>() {

        private var items: List<ChatMessageEntity> = emptyList()

        // buelltune-7ls: one Markwon instance per adapter (parsing/rendering
        // is not free), built lazily against the RecyclerView's own context
        // the first time a ViewHolder is created - never per bind.
        private var markwon: Markwon? = null

        @Suppress("NotifyDataSetChanged")
        fun submitList(list: List<ChatMessageEntity>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_message_item, parent, false)
            val resolvedMarkwon = markwon ?: Markwon.builder(parent.context.applicationContext)
                .usePlugin(TablePlugin.create(parent.context.applicationContext))
                .build()
                .also { markwon = it }
            return ViewHolder(view, resolvedMarkwon)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], onSuggestionTapped)
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View, private val markwon: Markwon) : RecyclerView.ViewHolder(view) {
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
             *
             * buelltune-7ls: only an assistant row's [ChatMessageEntity.content]
             * is rendered as markdown - a rider's own typed [text] is shown
             * verbatim, never reinterpreted as markdown syntax.
             */
            @Suppress("DiscouragedApi")
            fun bind(message: ChatMessageEntity, onSuggestionTapped: (String) -> Unit) {
                val isUser = message.role == Role.USER.name
                if (isUser) {
                    text.text = message.content
                } else {
                    markwon.setMarkdown(text, message.content)
                }

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

    companion object {
        private const val TAG = "ChatFragment"
    }
}

/** buelltune-1vt: a conversation list row's first-user-message preview, short enough to fit [R.layout.chat_conversation_item]'s single-line title. */
private fun truncatePreview(preview: String, maxLength: Int = 60): String =
    if (preview.length <= maxLength) preview else preview.take(maxLength - 1).trimEnd() + "\u2026"
