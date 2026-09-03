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
package biz.logicminds.buelltune.chat

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The minimal capability [ChatRepository] needs from a conversation's bound
 * [ChatAgent]: just [ChatAgent.send] itself. Real bindings ([asChatSender])
 * are a one-line adapter around a real [ChatAgent]; this seam exists purely
 * so a test can substitute a plain in-memory double that captures its
 * [ConversationTurn] arguments, without constructing a real Koog
 * `PromptExecutor`/`ToolRegistry` (KTD7's test scenario needs to prove what
 * reaches this call, not exercise a real provider).
 */
fun interface ChatSender {
    suspend fun send(text: String, priorTurns: List<ConversationTurn>): ChatAgentResult
}

/** Adapts a real [ChatAgent] to [ChatSender] - the production binding [ChatRepository]'s [ChatAgentFactory] constructor uses. */
fun ChatAgent.asChatSender(): ChatSender = ChatSender { text, priorTurns -> send(text, priorTurns) }

/**
 * Owns conversation/message persistence (R15) and the one place in this
 * codebase that reconstructs a resumed conversation's model-bound turn list
 * (KTD7, R17): [sendMessage] builds that list from [ChatMessageEntity.role]/
 * [ChatMessageEntity.content] only, by construction - it never even reads
 * [ChatMessageEntity.toolCallsJson] into a [ConversationTurn], so a prior
 * turn's tool payload cannot accidentally leak back into the model's
 * context after the app process restarts and a conversation is reopened.
 *
 * [bindAgent] resolves a [ChatSender] bound to a conversation's
 * provider/model (KD5) - the secondary constructor below is the production
 * shape, delegating to a real [ChatAgentFactory]. [ProviderCredentials] are
 * never persisted (secrets don't belong in [ChatDatabase]): [sendMessage]
 * takes them as a parameter on every call rather than caching them from
 * [createConversation] alone, since that cache would be empty - and every
 * resumed conversation's first send would fail - after any fresh app
 * process start (the normal case for R15's "browsable list the rider can
 * switch between" across app restarts). The resulting [ChatSender] itself
 * IS cached per conversation, once built, for the lifetime of this
 * repository instance, so credentials are only consulted again if this
 * instance's cache was never populated for that conversation.
 */
class ChatRepository(
    private val database: ChatDatabase,
    private val bindAgent: (ProviderId, ProviderCredentials, EcmTools, (String) -> Unit) -> ChatSender,
) {
    /**
     * Back-compat binding shape for callers/tests that don't care about
     * in-flight tool-call names - the trailing `onToolCallStarting`
     * callback below is simply discarded. The production
     * [ChatDatabase]/[ChatAgentFactory] constructor wires it for real (see
     * [toolCallEvents]).
     */
    constructor(database: ChatDatabase, bindAgent: (ProviderId, ProviderCredentials, EcmTools) -> ChatSender) : this(
        database,
        { providerId, credentials, ecmTools, _ -> bindAgent(providerId, credentials, ecmTools) },
    )

    constructor(database: ChatDatabase, chatAgentFactory: ChatAgentFactory) : this(
        database,
        { providerId, credentials, ecmTools, onToolCallStarting ->
            chatAgentFactory.create(providerId, credentials, ecmTools, onToolCallStarting).asChatSender()
        },
    )

    private val agentsByConversation = ConcurrentHashMap<Long, ChatSender>()

    private val toolCallEventsMutable = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /**
     * Per-call "Reading ECM: `<tool>`" names (R12/R19), emitted while any
     * bound [ChatAgent]'s [sendMessage] call is mid-turn. Not scoped to a
     * conversation id: the UI only observes this for the duration of its
     * own in-flight [sendMessage] call, so cross-conversation interleaving
     * (this app has no concurrent-send UI path) is not a concern.
     */
    val toolCallEvents: SharedFlow<String> = toolCallEventsMutable.asSharedFlow()

    /** The rider-browsable conversation list (R15), newest first. */
    val conversations: Flow<List<ConversationEntity>>
        get() = database.conversationDao().observeAll()

    /** One conversation's turns, oldest first, for the transcript UI (R12) - includes [ChatMessageEntity.toolCallsJson] for display. */
    fun messages(conversationId: Long): Flow<List<ChatMessageEntity>> =
        database.chatMessageDao().observeForConversation(conversationId)

    /**
     * Persists a new conversation bound to [providerId]/[modelId] (KD5) -
     * immutable from here on; no method on this class ever updates either
     * column afterward.
     */
    suspend fun createConversation(providerId: ProviderId, modelId: String, title: String): Long =
        database.conversationDao().insert(
            ConversationEntity(
                title = title,
                providerId = providerId.name,
                modelId = modelId,
                createdAt = System.currentTimeMillis(),
            ),
        )

    /** Deletes [conversationId] and, via [ChatMessageEntity]'s cascading foreign key, every one of its messages. */
    suspend fun deleteConversation(conversationId: Long) {
        database.conversationDao().deleteById(conversationId)
        agentsByConversation.remove(conversationId)
    }

    /**
     * Sends [text] on [conversationId]: persists it immediately as a user
     * turn, replays every prior turn's `role`/`content` (KTD7 - never
     * `toolCallsJson`) to the bound [ChatSender] alongside [text], then
     * persists the assistant's reply with [ChatAgentResult.toolsCalled]
     * JSON-encoded for on-screen display only (R12), plus
     * [ChatAgentResult.suggestion] (if any) as [ChatMessageEntity.suggestionScreenId]/
     * [ChatMessageEntity.suggestionLabel] so a rendered [SuggestionCard]
     * survives conversation resume (R2, AE3) instead of being discarded
     * once the in-memory [ChatAgentResult] goes out of scope. [credentials]
     * is only actually consulted the first time this repository instance
     * binds this conversation's [ChatSender] (see class doc) - callers
     * always pass the caller's currently-configured credentials for
     * [ConversationEntity.providerId], typically read fresh from
     * `AppPreferences` immediately before the call.
     */
    suspend fun sendMessage(conversationId: Long, text: String, ecmTools: EcmTools, credentials: ProviderCredentials) {
        val messageDao = database.chatMessageDao()
        val userMessageId = messageDao.insert(
            ChatMessageEntity(
                conversationId = conversationId,
                role = Role.USER.name,
                content = text,
                toolCallsJson = null,
                createdAt = System.currentTimeMillis(),
            ),
        )

        // KTD7: role/content only, and explicitly excluding the user row
        // just inserted above - it is passed to ChatSender.send separately
        // as `text`, so including it here too would duplicate this turn.
        val priorTurns = messageDao.observeForConversation(conversationId).first()
            .filterNot { it.id == userMessageId }
            .map { ConversationTurn(role = Role.valueOf(it.role), content = it.content) }

        val sender = resolveAgent(conversationId, ecmTools, credentials)
        val result = sender.send(text, priorTurns)

        messageDao.insert(
            ChatMessageEntity(
                conversationId = conversationId,
                role = Role.ASSISTANT.name,
                content = result.displayText,
                toolCallsJson = result.toolsCalled.takeIf { it.isNotEmpty() }
                    ?.let { Json.encodeToString(ListSerializer(String.serializer()), it) },
                suggestionScreenId = result.suggestion?.screenId,
                suggestionLabel = result.suggestion?.label,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun resolveAgent(conversationId: Long, ecmTools: EcmTools, credentials: ProviderCredentials): ChatSender {
        agentsByConversation[conversationId]?.let { return it }
        val conversation = database.conversationDao().observeAll().first()
            .firstOrNull { it.id == conversationId }
            ?: error("Unknown conversation $conversationId")
        val sender = bindAgent(ProviderId.valueOf(conversation.providerId), credentials, ecmTools) { toolName ->
            toolCallEventsMutable.tryEmit(toolName)
        }
        agentsByConversation[conversationId] = sender
        return sender
    }
}
