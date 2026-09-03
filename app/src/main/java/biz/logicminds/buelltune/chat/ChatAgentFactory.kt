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

import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLModel

/**
 * Credentials for the provider a conversation is bound to (KD5): [apiKey]
 * covers every provider but Ollama, [baseUrl] is Ollama's rider-reachable
 * server (R11 - no on-device inference).
 */
data class ProviderCredentials(
    val apiKey: String? = null,
    val baseUrl: String? = null,
)

/**
 * v1 placeholder instructing the model on the [SuggestionCard] marker
 * convention (KTD8) so the suggestion mechanism (R2) works end-to-end today.
 * `SystemPrompt.CONTENT`'s full DDFI-2 domain-fact seeding (R21, R22) is
 * U10's file (`chat/SystemPrompt.kt`, depends on this unit) - this constant
 * is superseded there, not duplicated.
 */
private const val PLACEHOLDER_SYSTEM_PROMPT = """
You are a diagnostic assistant for a Buell motorcycle's DDFI-2 ECM. You can
read the bike's current and stored state through the tools provided, but you
can never write, reset, or flash anything - those tools do not exist for you.

When you conclude the rider should perform a write, reset, or flash, end your
answer with exactly one fenced line in this form, naming the existing screen
where the rider performs the action manually:
[[SUGGEST:<drawer-item-id>|<short action label>]]
For example: [[SUGGEST:nav_setup|Reset TPS zero]]
Never claim to have performed the action yourself.
"""

/**
 * Resolves a Koog [PromptExecutor]/[LLModel] pair for [providerId] and binds
 * it, plus [ecmTools]' read-only tool registry, into a [ChatAgent]. This is
 * the only place a real, network-calling [PromptExecutor] gets constructed
 * (KTD6) - [ChatAgent] itself takes one as a plain constructor parameter, so
 * a test double stands in without touching this class.
 */
class ChatAgentFactory {

    fun create(
        providerId: ProviderId,
        credentials: ProviderCredentials,
        ecmTools: EcmTools,
        onToolCallStarting: (String) -> Unit = {},
    ): ChatAgent {
        val (executor, model) = resolveExecutorAndModel(providerId, credentials)
        return ChatAgent(
            promptExecutor = executor,
            llmModel = model,
            toolRegistry = ecmToolRegistry(ecmTools),
            systemPrompt = PLACEHOLDER_SYSTEM_PROMPT,
            onToolCallStarting = onToolCallStarting,
        )
    }

    private fun resolveExecutorAndModel(
        providerId: ProviderId,
        credentials: ProviderCredentials,
    ): Pair<PromptExecutor, LLModel> = when (providerId) {
        ProviderId.ANTHROPIC -> {
            val apiKey = requireApiKey(providerId, credentials)
            MultiLLMPromptExecutor(AnthropicLLMClient(apiKey)) to AnthropicModels.Opus_4_1
        }
        ProviderId.OPENAI -> {
            val apiKey = requireApiKey(providerId, credentials)
            MultiLLMPromptExecutor(OpenAILLMClient(apiKey)) to OpenAIModels.Chat.GPT4o
        }
        ProviderId.GOOGLE -> {
            val apiKey = requireApiKey(providerId, credentials)
            MultiLLMPromptExecutor(GoogleLLMClient(apiKey)) to GoogleModels.Gemini2_5Pro
        }
        ProviderId.DEEPSEEK -> {
            val apiKey = requireApiKey(providerId, credentials)
            MultiLLMPromptExecutor(DeepSeekLLMClient(apiKey)) to DeepSeekModels.DeepSeekV4Flash
        }
        ProviderId.OPENROUTER -> {
            // Also the Kimi/Moonshot path (KD4) - no special-casing needed.
            val apiKey = requireApiKey(providerId, credentials)
            MultiLLMPromptExecutor(OpenRouterLLMClient(apiKey)) to OpenRouterModels.GPT4o
        }
        ProviderId.OLLAMA -> {
            val baseUrl = credentials.baseUrl?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Ollama requires a base URL.")
            MultiLLMPromptExecutor(OllamaClient(baseUrl)) to OllamaModels.Meta.LLAMA_3_2
        }
    }

    private fun requireApiKey(providerId: ProviderId, credentials: ProviderCredentials): String {
        val apiKey = credentials.apiKey
        if (apiKey.isNullOrBlank()) {
            throw IllegalArgumentException("$providerId requires an API key.")
        }
        return apiKey
    }
}
