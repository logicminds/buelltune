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

import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import biz.logicminds.buelltune.BitSetProvider
import biz.logicminds.buelltune.Constants
import biz.logicminds.buelltune.ECM
import biz.logicminds.buelltune.EcmDefinitionsProvider
import biz.logicminds.buelltune.Variable
import biz.logicminds.buelltune.VariableProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room needs a real Android [android.content.Context] and its bundled SQLite
 * driver ([JdbcEcmDefinitions.kt][biz.logicminds.buelltune.integration] -
 * plain `test` JVM unit tests already document this for
 * [biz.logicminds.buelltune.data.EcmDefinitionsDatabase], and no
 * Robolectric/KMP-bundled-driver dependency exists in this project to work
 * around it (checked `gradle/libs.versions.toml` and `app/build.gradle.kts`:
 * no Robolectric artifact is declared). This suite therefore lives under
 * `androidTest`, exactly like this repo's existing
 * `TestEcmDefinitionsDatabase` Room instrumented test, using
 * [Room.inMemoryDatabaseBuilder] against a fresh [ChatDatabase] per test.
 */
@RunWith(AndroidJUnit4::class)
class TestChatRepository {

    private lateinit var database: ChatDatabase

    /** A minimal, side-effect-free [EcmTools] - never invoked by any fake [ChatSender] below, just needed to satisfy [ChatRepository.sendMessage]'s signature. */
    private val fakeEcmTools = EcmTools(
        ECM(FakeVariableProvider, FakeBitSetProvider, FakeDefinitionsProvider, null),
        FakeVariableProvider,
        FakeDefinitionsProvider,
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChatDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun resumedConversation_repliesOmitPriorToolCallsFromModelContext() = runBlocking {
        val capturedPriorTurns = mutableListOf<List<ConversationTurn>>()
        val repository = ChatRepository(database) { _, _, _, _, _ ->
            ChatSender { _, priorTurns ->
                capturedPriorTurns.add(priorTurns)
                ChatAgentResult(displayText = "Reply", suggestion = null, toolsCalled = emptyList())
            }
        }

        val conversationId = repository.createConversation(
            providerId = ProviderId.ANTHROPIC,
            modelId = "claude-test",
            title = "Resumed conversation",
        )

        // Simulate a resumed conversation: a prior assistant turn whose
        // toolCallsJson carries a real tool payload, inserted directly
        // (as if written by an earlier sendMessage call in a past process).
        database.chatMessageDao().insert(
            ChatMessageEntity(
                conversationId = conversationId,
                role = Role.USER.name,
                content = "What's my current RPM?",
                toolCallsJson = null,
                createdAt = 1L,
            ),
        )
        database.chatMessageDao().insert(
            ChatMessageEntity(
                conversationId = conversationId,
                role = Role.ASSISTANT.name,
                content = "Your current RPM is 1200.",
                toolCallsJson = """["read_live_data"]""",
                createdAt = 2L,
            ),
        )

        repository.sendMessage(conversationId, "Has that changed?", fakeEcmTools, ProviderCredentials(apiKey = "test-key"))

        assertEquals(1, capturedPriorTurns.size)
        val priorTurns = capturedPriorTurns.single()
        // Structurally: ConversationTurn only ever carries role/content, so
        // reading it back can never surface toolCallsJson - and the content
        // that *did* cross the boundary matches the persisted text exactly,
        // with no trace of the tool payload string.
        assertEquals(
            listOf(
                ConversationTurn(Role.USER, "What's my current RPM?"),
                ConversationTurn(Role.ASSISTANT, "Your current RPM is 1200."),
            ),
            priorTurns,
        )
        assertTrue(priorTurns.none { it.content.contains("read_live_data") })
        assertTrue(priorTurns.none { it.content.contains("toolCallsJson") })
    }

    @Test
    fun newConversation_persistsProviderAndModelImmutablyAtCreation() = runBlocking {
        val repository = ChatRepository(database) { _, _, _, _, _ ->
            ChatSender { _, _ -> ChatAgentResult(displayText = "unused", suggestion = null, toolsCalled = emptyList()) }
        }

        val conversationId = repository.createConversation(
            providerId = ProviderId.OPENAI,
            modelId = "gpt-test-model",
            title = "New conversation",
        )

        val persisted = repository.conversations.first().single { it.id == conversationId }
        assertEquals(ProviderId.OPENAI.name, persisted.providerId)
        assertEquals("gpt-test-model", persisted.modelId)

        // Structural/API-surface check (KTD5): ChatRepository must expose no
        // method that could mutate a conversation's provider/model after
        // creation.
        val updateMethods = ChatRepository::class.java.declaredMethods.filter { method ->
            val name = method.name.lowercase()
            name.contains("update") && (name.contains("provider") || name.contains("model"))
        }
        assertTrue("ChatRepository must not expose an update-provider/update-model method, found: $updateMethods", updateMethods.isEmpty())
    }

    @Test
    fun listModels_delegatesToTheInjectedQueryModelsFunctionWithTheSameArguments() = runBlocking {
        var capturedProviderId: ProviderId? = null
        var capturedCredentials: ProviderCredentials? = null
        val fakeModels = listOf(
            LLModel(provider = LLMProvider.Anthropic, id = "fake-model-a"),
            LLModel(provider = LLMProvider.Anthropic, id = "fake-model-b"),
        )
        val repository = ChatRepository(
            database = database,
            queryModels = { providerId, credentials ->
                capturedProviderId = providerId
                capturedCredentials = credentials
                fakeModels
            },
            bindAgent = { _, _, _, _, _ -> ChatSender { _, _ -> ChatAgentResult(displayText = "unused", suggestion = null, toolsCalled = emptyList()) } },
        )

        val result = repository.listModels(ProviderId.ANTHROPIC, ProviderCredentials(apiKey = "test-key"))

        assertEquals(fakeModels, result)
        assertEquals(ProviderId.ANTHROPIC, capturedProviderId)
        assertEquals(ProviderCredentials(apiKey = "test-key"), capturedCredentials)
    }

    @Test
    fun deletingConversation_removesItsMessages() = runBlocking {
        val repository = ChatRepository(database) { _, _, _, _, _ ->
            ChatSender { _, _ -> ChatAgentResult(displayText = "Reply", suggestion = null, toolsCalled = listOf("read_live_data")) }
        }

        val conversationId = repository.createConversation(
            providerId = ProviderId.ANTHROPIC,
            modelId = "claude-test",
            title = "Doomed conversation",
        )
        repository.sendMessage(conversationId, "Hello", fakeEcmTools, ProviderCredentials(apiKey = "test-key"))
        assertEquals(2, repository.messages(conversationId).first().size)

        repository.deleteConversation(conversationId)

        assertTrue(repository.conversations.first().none { it.id == conversationId })
        assertTrue(repository.messages(conversationId).first().isEmpty())
    }

    private object FakeVariableProvider : VariableProvider() {
        override fun getRtVariableNames(ecm: String): Collection<String> = emptyList()
        override fun getRtVariable(ecm: String, name: String): Variable? = null
        override fun getScalarRtVariableNames(ecm: String): Collection<String> = emptyList()
        override fun getBitfieldRtVariableNames(ecm: String): Collection<String> = emptyList()
        override fun getEEPROMVariable(ecm: String, name: String): Variable? = null
        override fun getName(varname: String): String? = null
        override fun getName(varname: String, bitnumber: Int): String? = null
        override fun getNearestEEPROMVariable(ecm: String, offset: Int): Variable? = null
    }

    private object FakeBitSetProvider : BitSetProvider() {
        override fun getBitSet(ecmId: String, name: String, source: Constants.DataSource) = null
    }

    private object FakeDefinitionsProvider : EcmDefinitionsProvider {
        override fun getEeprom(ecmId: String) = null
        override fun size2id(length: Int): List<String> = emptyList()
    }
}
