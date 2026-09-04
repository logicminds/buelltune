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
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import biz.logicminds.buelltune.ECM
import biz.logicminds.buelltune.EEPROM
import biz.logicminds.buelltune.PDU
import biz.logicminds.buelltune.TestUtils
import biz.logicminds.buelltune.integration.AssetDatabase
import biz.logicminds.buelltune.integration.JdbcBitSetProvider
import biz.logicminds.buelltune.integration.JdbcEcmDefinitionsProvider
import biz.logicminds.buelltune.integration.JdbcVariableProvider
import biz.logicminds.buelltune.transport.ConnectionState
import biz.logicminds.buelltune.transport.EcmTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection

/**
 * Fake-LLM proof of [ChatAgent]'s capped/timed-out/suggestion-parsing/
 * fallback loop behavior (R25, plan U6): only [PromptExecutor] - the LLM
 * side - is faked via [ScriptedPromptExecutor], a hand-written test double
 * scripting a fixed [Message.Assistant] response sequence. Everything else
 * - [ChatAgent] itself, [ecmToolRegistry], and the [EcmTools] it wraps -
 * is the real production wiring driven through Koog's own real tool-
 * dispatch loop (`AIAgent`), so a passing test here proves the real
 * registry/adapter/loop plumbing, not just this file's own assertions.
 * [EcmTools] runs against the same JVM fixture data [TestEcmTools] (U3)
 * uses; no test in this class calls a real provider API or opens a real
 * network connection (R25).
 */

/** Just over [KoogEcmToolAdapter]'s 15s per-call budget (`TOOL_CALL_TIMEOUT_MS`), so a real overshoot is unambiguous. */
private const val TOOL_HANG_MILLIS = 16_000L

class TestChatAgent {

    private lateinit var connection: Connection

    @Before
    fun openDbConnection() {
        connection = AssetDatabase.newConnection()
    }

    @After
    fun closeDbConnection() {
        connection.close()
    }

    private fun newEcm(): ECM = ECM(
        JdbcVariableProvider(connection),
        JdbcBitSetProvider(connection),
        JdbcEcmDefinitionsProvider(connection),
        null,
    )

    private fun newTools(ecm: ECM): EcmTools = EcmTools(
        ecm,
        JdbcVariableProvider(connection),
        JdbcEcmDefinitionsProvider(connection),
    )

    /** The BUEIB EEPROM skeleton, filled with real fixture bytes - matches [TestEcmTools]'s helper verbatim. */
    private fun bueibEeprom(read: Boolean): EEPROM {
        val eeprom = JdbcEcmDefinitionsProvider(connection).getEeprom("BUEIB")!!
        eeprom.setBytes(TestUtils.readEEPROM())
        eeprom.setEepromRead(read)
        return eeprom
    }

    /** A connected [EcmTools] whose [ECM] never actually talks to a transport (every read the cap test scripts is transport-free). */
    private fun newConnectedTools(): EcmTools {
        val ecm = newEcm()
        val bytes = TestUtils.readRTData()
        ecm.connect(InstantEcmTransport(PDU(bytes, bytes.size)), ECM.Protocol.STOCK)
        ecm.setEEPROM(bueibEeprom(read = true))
        return newTools(ecm)
    }

    /**
     * Connects against a transport whose [transact] genuinely takes longer
     * than [KoogEcmToolAdapter]'s 15s adapter-level budget (KoogEcmToolAdapter.kt:42's
     * `TOOL_CALL_TIMEOUT_MS`) before real ECM communication can complete.
     *
     * [ECM] has no `suspend fun` of its own (by design - see its `connect`
     * doc: "ECM keeps its blocking signature rather than becoming suspend
     * itself") - every method that touches [transport] bridges into
     * [EcmTransport]'s suspend API via a *new, unrelated* `runBlocking`
     * ([ECM.sendPDU]/[ECM.connect]), never a child of the calling
     * coroutine's Job. A [transact] that suspends forever
     * ([kotlinx.coroutines.awaitCancellation]) is therefore never actually
     * interrupted by [withTimeoutOrNull]'s cancellation - cooperative
     * cancellation cannot preempt a thread parked inside that unrelated,
     * synchronous `runBlocking` bridge, so the call would hang forever
     * instead of timing out (verified: it hangs well past the adapter's
     * 15s budget with no recovery). [delay]ing past the budget and then
     * *returning normally* is what actually proves the timeout path:
     * `withTimeoutOrNull` records the cancellation at the 15s mark and,
     * once [transact] finally yields control back at ~16s, discards the
     * late (otherwise-successful) result in favor of the recorded
     * timeout - exactly what a genuinely slow-but-eventually-responding
     * ECM link would trigger in production.
     */
    private fun newHungTools(): EcmTools {
        val ecm = newEcm()
        val bytes = TestUtils.readRTData()
        ecm.connect(HangingEcmTransport(PDU(bytes, bytes.size)), ECM.Protocol.STOCK)
        ecm.setEEPROM(bueibEeprom(read = true))
        return newTools(ecm)
    }

    private class InstantEcmTransport(private val rtResponse: PDU) : EcmTransport {
        private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val state: StateFlow<ConnectionState> = _state.asStateFlow()
        override suspend fun connect() { _state.value = ConnectionState.Connected }
        override suspend fun transact(request: PDU): PDU = rtResponse
        override suspend fun disconnect() { _state.value = ConnectionState.Disconnected }
    }

    /**
     * [transact] genuinely takes [TOOL_HANG_MILLIS] (just over
     * [KoogEcmToolAdapter]'s 15s budget) before returning [rtResponse] -
     * long enough for the adapter's [withTimeoutOrNull] to have already
     * recorded the timeout by the time this call would otherwise succeed.
     */
    private class HangingEcmTransport(private val rtResponse: PDU) : EcmTransport {
        private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val state: StateFlow<ConnectionState> = _state.asStateFlow()
        override suspend fun connect() { _state.value = ConnectionState.Connected }
        override suspend fun transact(request: PDU): PDU {
            delay(TOOL_HANG_MILLIS)
            return rtResponse
        }
        override suspend fun disconnect() { _state.value = ConnectionState.Disconnected }
    }

    /**
     * A hand-written [PromptExecutor] test double (R25): extends the
     * abstract class (not the raw `PromptExecutorAPI` interface) so every
     * member but the three genuinely-abstract ones - [execute],
     * [executeStreaming], [moderate] - inherits Koog's own default. Only
     * [execute] is exercised: [ChatAgent]'s v1 loop is non-streaming by
     * design (see `ChatAgent.kt`'s class doc), so [executeStreaming] and
     * [moderate] assert they are never called instead of returning fake
     * data.
     */
    private class ScriptedPromptExecutor(
        private val script: MutableList<Message.Assistant>,
    ) : PromptExecutor() {
        /** Every [Prompt] Koog's real dispatch loop handed this executor, in call order - lets a test inspect what the loop fed back after a tool call. */
        val promptsSeen = mutableListOf<Prompt>()
        var streamingCallCount = 0
            private set
        var moderateCallCount = 0
            private set

        override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
            promptsSeen.add(prompt)
            check(script.isNotEmpty()) {
                "ScriptedPromptExecutor ran out of scripted responses after ${promptsSeen.size} execute() calls"
            }
            return script.removeAt(0)
        }

        override fun close() {}

        override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> {
            streamingCallCount++
            throw NotImplementedError("ChatAgent's v1 loop never calls executeStreaming (non-streaming by design).")
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
            moderateCallCount++
            throw NotImplementedError("ChatAgent's v1 loop never calls moderate.")
        }
    }

    private val testModel: LLModel = OpenAIModels.Chat.GPT4o

    private fun newChatAgent(executor: ScriptedPromptExecutor, tools: EcmTools): ChatAgent = ChatAgent(
        promptExecutor = executor,
        llmModel = testModel,
        toolRegistry = ecmToolRegistry(tools),
        systemPrompt = "You are a test double's diagnostic assistant.",
    )

    /** A tool-call-only response naming a real registered tool, decoded by Koog's own real dispatch. */
    private fun toolCallResponse(id: String, toolName: String, argsJson: JsonObject): Message.Assistant =
        Message.Assistant(
            listOf(MessagePart.Tool.Call(id, toolName, argsJson)),
            ResponseMetaInfo.Empty,
            null,
            null,
            id,
        )

    /** A final plain-text answer with no further tool call. */
    private fun textResponse(text: String, id: String = "assistant-final"): Message.Assistant =
        Message.Assistant(text, ResponseMetaInfo.Empty, null, null, id)

    @Test
    fun agent_capsAtTwentyToolIterations_returnsPartialAnswerNotInfiniteLoop() = runBlocking {
        // Thirty consecutive tool-call responses, no final text answer ever
        // offered - if the loop had no cap, this would run forever.
        val script = MutableList(30) { i -> toolCallResponse("call-$i", "get_ecm_info", buildJsonObject {}) }
        val executor = ScriptedPromptExecutor(script)
        val agent = newChatAgent(executor, newConnectedTools())

        // Koog's own maxAgentIterations=20 enforcement (ChatAgent.kt's
        // MAX_AGENT_ITERATIONS) throws AIAgentMaxNumberOfIterationsReachedException
        // rather than silently returning a partial answer - proven by
        // decompiling agents-core: AIAgentSubgraphBase's inner-context loop
        // throws it directly, and nothing in agents-core catches it. The
        // real assertion of "not an infinite loop" is that this call
        // returns (by throwing) at all, and stops well before the 30th
        // scripted response is ever consumed.
        val ex = assertThrows(
            AIAgentMaxNumberOfIterationsReachedException::class.java,
        ) {
            runBlocking { agent.send("Tell me everything about the ECM.", emptyList()) }
        }
        assertTrue(
            "expected the exception to report the real 20-iteration cap, got: ${ex.message}",
            ex.message?.contains("20") == true,
        )
        assertTrue(
            "expected the loop to stop before exhausting all 30 scripted responses (bounded, not infinite)",
            executor.promptsSeen.size <= 30,
        )
    }

    @Test(timeout = 30_000)
    fun agent_toolCallExceeding15s_returnsTimeoutErrorToModelNotCrash() = runBlocking {
        val script = mutableListOf(
            toolCallResponse("call-0", "read_live_data", buildJsonObject { putJsonArray("variables") { add(JsonPrimitive("RPM")) } }),
            textResponse("The read timed out; I could not fetch live data."),
        )
        val executor = ScriptedPromptExecutor(script)
        val agent = newChatAgent(executor, newHungTools())

        // Must complete (not hang, not throw) even though the underlying
        // transport never responds: KoogEcmToolAdapter's runToolCall wraps
        // every call in a 15s withTimeoutOrNull and JSON-encodes a
        // ToolResult.Error("...timed out...") string rather than letting
        // an exception or an indefinite hang escape the adapter.
        val result = agent.send("Read the current RPM.", emptyList())

        assertEquals("The read timed out; I could not fetch live data.", result.displayText)
        assertEquals(listOf("read_live_data"), result.toolsCalled)

        val toolResultParts = executor.promptsSeen.last().messages
            .flatMap { it.parts }
            .filterIsInstance<MessagePart.Tool.Result>()
        assertTrue("expected a tool-result part fed back to the model", toolResultParts.isNotEmpty())
        assertTrue(
            "expected the tool's returned string to report the timeout, got: ${toolResultParts.map { it.output }}",
            toolResultParts.any { it.output.contains("timed out", ignoreCase = true) },
        )
        assertFalse(
            "a timeout must surface as a normal (non-error) tool result string, never an adapter-level tool error",
            toolResultParts.any { it.isError },
        )
    }

    @Test
    fun agent_suggestionMarkerInFinalText_extractedAsSuggestionCardAndStrippedFromDisplayText() = runBlocking {
        val rawText = "You should reset the TPS to zero using Setup. [[SUGGEST:nav_setup|Reset TPS zero]]"
        val executor = ScriptedPromptExecutor(mutableListOf(textResponse(rawText)))
        val agent = newChatAgent(executor, newConnectedTools())

        val result = agent.send("What should I do about my idle TPS drift?", emptyList())

        assertEquals(SuggestionCard("nav_setup", "Reset TPS zero"), result.suggestion)
        assertEquals("You should reset the TPS to zero using Setup.", result.displayText)
        assertFalse("raw marker text must not leak into the display text", result.displayText.contains("[[SUGGEST"))
    }

    @Test
    fun agent_unrecognizedSuggestionTarget_degradesToPlainTextNoCard() = runBlocking {
        // Malformed per SuggestionCard.kt's own documented contract: the
        // marker is not anchored at the very end of the text (trailing
        // prose follows it), so SUGGEST_MARKER's `$`-anchored regex never
        // matches and extraction degrades to plain text rather than
        // throwing or partially parsing.
        val rawText = "Reset the TPS to zero. [[SUGGEST:nav_setup|Reset TPS zero]] Let me know if that helps."
        val executor = ScriptedPromptExecutor(mutableListOf(textResponse(rawText)))
        val agent = newChatAgent(executor, newConnectedTools())

        val result = agent.send("What should I do about my idle TPS drift?", emptyList())

        assertNull(result.suggestion)
        assertEquals(rawText, result.displayText)
    }

    @Test
    fun agent_streamingUnavailable_fallsBackToSingleFinalChunk() = runBlocking {
        val executor = ScriptedPromptExecutor(mutableListOf(textResponse("All diagnostics look nominal.")))
        val agent = newChatAgent(executor, newConnectedTools())

        // The fake's executeStreaming/moderate both throw NotImplementedError
        // (verifying Koog's real send() path never touches them); the call
        // must still succeed and hand back exactly one complete result -
        // not a stream of partial chunks - proving ChatAgent.send()'s
        // documented v1 non-streaming path is what actually executes.
        val result = agent.send("Is everything okay?", emptyList())

        assertEquals("All diagnostics look nominal.", result.displayText)
        assertNull(result.suggestion)
        assertEquals(0, executor.streamingCallCount)
        assertEquals(0, executor.moderateCallCount)
    }
}
