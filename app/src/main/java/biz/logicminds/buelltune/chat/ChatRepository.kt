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

import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.CancellationException
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
 * takes them as a parameter on every call, and [resolveAgent] rebinds a
 * fresh [ChatSender] from them on every call too (never cached across
 * calls) - [ChatAgentFactory.create] does no network I/O until the
 * returned agent actually runs, so this costs nothing per send, and it is
 * the only way an in-[biz.logicminds.buelltune.activities.LlmSettingsActivity]
 * credential edit takes effect on an already-open conversation's very next
 * turn, matching the contract `AppPreferences.credentialsFor`'s KDoc
 * documents ("no app restart needed").
 */
class ChatRepository(
    private val database: ChatDatabase,
    private val queryModels: suspend (ProviderId, ProviderCredentials) -> List<LLModel> = { providerId, _ ->
        throw UnsupportedOperationException("No ChatAgentFactory bound - listModels needs the production ChatRepository(database, chatAgentFactory) constructor (queried for $providerId).")
    },
    private val bindAgent: (ProviderId, ProviderCredentials, String, EcmTools, (String) -> Unit) -> ChatSender,
) {
    constructor(database: ChatDatabase, chatAgentFactory: ChatAgentFactory) : this(
        database,
        { providerId, credentials -> chatAgentFactory.listModels(providerId, credentials) },
        { providerId, credentials, modelId, ecmTools, onToolCallStarting ->
            chatAgentFactory.create(providerId, credentials, modelId, ecmTools, onToolCallStarting).asChatSender()
        },
    )

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
     * are always passed straight through to [resolveAgent] on every call -
     * callers should read them fresh from `AppPreferences` immediately
     * before calling.
     *
     * A failing [ChatSender.send] (Koog's tool-iteration cap, a malformed
     * provider response, any other exception) is caught here rather than
     * left to propagate: the user's turn above is already persisted by the
     * time [ChatSender.send] runs, and every future call on this
     * conversation replays every persisted turn as model context (KTD7)
     * - leaving that turn without a matching assistant reply would silently
     * feed a broken, unanswered turn back into the model forever. A short
     * failure message is persisted as the assistant reply instead, so the
     * conversation's turn alternation - and this call's caller - both see a
     * definite, recoverable outcome.
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
        val result = try {
            sender.send(text, priorTurns)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AIAgentMaxNumberOfIterationsReachedException) {
            ChatAgentResult(
                displayText = "I hit the tool-call limit for this turn before finishing - try asking a narrower question.",
                suggestion = null,
                toolsCalled = emptyList(),
            )
        } catch (e: Exception) {
            ChatAgentResult(
                displayText = "Something went wrong answering that (${e.message ?: e::class.simpleName}). Try again.",
                suggestion = null,
                toolsCalled = emptyList(),
            )
        }

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

    /**
     * Live "what can I actually pick" query for the new-conversation model
     * picker (R21) - a real network call to [providerId] using
     * [credentials], never cached, so it reflects whatever the account
     * actually has access to right now. See [ChatAgentFactory.listModels]
     * for the filtering/fallback rules.
     */
    suspend fun listModels(providerId: ProviderId, credentials: ProviderCredentials): List<LLModel> =
        queryModels(providerId, credentials)

    private suspend fun resolveAgent(conversationId: Long, ecmTools: EcmTools, credentials: ProviderCredentials): ChatSender {
        val conversation = database.conversationDao().observeAll().first()
            .firstOrNull { it.id == conversationId }
            ?: error("Unknown conversation $conversationId")
        return bindAgent(ProviderId.valueOf(conversation.providerId), credentials, conversation.modelId, ecmTools) { toolName ->
            toolCallEventsMutable.tryEmit(toolName)
        }
    }
}
