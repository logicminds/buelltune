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

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import java.util.UUID

/** One text turn as it is persisted/replayed - never a tool call/result payload (KTD7). */
enum class Role { USER, ASSISTANT }

/** A single prior turn `ChatRepository` (a later unit) reconstructs from `ChatMessageEntity.role`/`content`. */
data class ConversationTurn(val role: Role, val content: String)

/**
 * The outcome of one [ChatAgent.send] call: the model's final answer with any
 * [SuggestionCard] marker stripped (KTD8), the parsed card itself, and the
 * names of every ECM tool the loop invoked while producing it - what a later
 * UI unit renders as "Reading ECM: `<tool>`" per-call indicators (R12).
 */
data class ChatAgentResult(
    val displayText: String,
    val suggestion: SuggestionCard?,
    val toolsCalled: List<String>,
)

private const val MAX_AGENT_ITERATIONS = 5

/**
 * Drives one turn of the capped, timed-out, tool-calling agentic loop (R12)
 * against a single provider/model bound for this conversation's lifetime
 * (KD5) - [promptExecutor], [llmModel] and [toolRegistry] are supplied by
 * [ChatAgentFactory], never constructed here, so a test double for
 * [PromptExecutor] can stand in without touching this class.
 *
 * A fresh Koog `AIAgent` is built per [send] call, its prompt seeded from
 * [priorTurns] via [AIAgentConfig] using only `system`/`user`/`assistant`
 * text turns (KTD7) - no prior tool_use/tool_result payload from an earlier
 * turn ever re-enters the model's context, so any state-dependent follow-up
 * on a resumed conversation structurally re-fetches fresh ECM state (R16,
 * R17) rather than trusting model compliance.
 *
 * Responses are non-streaming (R13): Koog's tool-calling `AIAgent.run()`
 * loop has no verified simple streaming hook in 1.2.0 - `LLMClient`'s own
 * `executeStreaming()` exists, but wiring it through a tool-calling agent
 * loop needs advanced graph/functional-agent composition out of proportion
 * to this unit. This non-streaming path is the actual v1 implementation,
 * not a placeholder; streaming remains the explicitly deferred fallback.
 */
class ChatAgent(
    private val promptExecutor: PromptExecutor,
    private val llmModel: LLModel,
    private val toolRegistry: ToolRegistry,
    private val systemPrompt: String,
    private val onToolCallStarting: (String) -> Unit = {},
) {

    suspend fun send(userText: String, priorTurns: List<ConversationTurn>): ChatAgentResult {
        val toolsCalled = mutableListOf<String>()
        val seededPrompt = prompt(UUID.randomUUID().toString()) {
            system(systemPrompt)
            for (turn in priorTurns) {
                when (turn.role) {
                    Role.USER -> user(turn.content)
                    Role.ASSISTANT -> assistant(turn.content)
                }
            }
        }
        val agentConfig = AIAgentConfig(
            prompt = seededPrompt,
            model = llmModel,
            maxAgentIterations = MAX_AGENT_ITERATIONS,
        )
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        ) {
            handleEvents {
                onToolCallStarting { context ->
                    toolsCalled.add(context.toolName)
                    onToolCallStarting(context.toolName)
                }
            }
        }
        val rawResponse = agent.run(userText)
        val (displayText, suggestion) = extractSuggestion(rawResponse)
        return ChatAgentResult(displayText, suggestion, toolsCalled.toList())
    }
}
