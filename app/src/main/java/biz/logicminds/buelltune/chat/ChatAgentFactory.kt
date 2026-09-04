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

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Explicit [KoogHttpClient.Factory], bypassing Koog 1.2.0's own
 * ServiceLoader-based auto-discovery (`java.util.ServiceLoader.load`),
 * which is broken for the Android target: the `http-client-ktor-android`
 * artifact ships zero `META-INF/services` registration (confirmed by
 * inspecting the resolved AAR directly), so the default lookup always
 * throws `IllegalStateException("No KoogHttpClient.Factory provider
 * found...")` the first time any provider client is actually used - found
 * via manual smoke testing against `ecmsimRun` on a real device/emulator,
 * not caught by any unit/instrumented test (U6's agentic-loop tests use a
 * fake `PromptExecutor` that never exercises a real client's HTTP
 * construction path). Every provider client accepts a
 * [KoogHttpClient.Factory] as an explicit constructor parameter
 * specifically for this fallback (confirmed by inspecting each client's
 * compiled constructors); [KtorKoogHttpClient.Factory] is Koog's own ready
 * implementation of it (headers/query params/timeouts/JSON content
 * negotiation all wired already) - it just needs a concrete Ktor engine,
 * which the app already depends on transitively (`ktor-client-okhttp`).
 */
private val koogHttpClientFactory: KoogHttpClient.Factory =
    KtorKoogHttpClient.Factory(HttpClient(OkHttp))

/** Moonshot AI's global OpenAI-protocol-compatible endpoint (platform.kimi.ai docs). */
private const val KIMI_DEFAULT_BASE_URL = "https://api.moonshot.ai/v1"

/**
 * Koog 1.2.0 has no first-class Kimi/Moonshot client or model catalog
 * (unlike OpenAI, Anthropic, etc.), so this is a hand-built [LLModel] for
 * Moonshot's current flagship chat-completions model id, `kimi-k2.6`
 * (confirmed against platform.kimi.ai's own Chat Completions API docs,
 * which document Tool Use/function calling support - required for
 * [EcmTools]). [LLMProvider.OpenAI] is deliberately reused as the
 * capability/request-shaping profile: Moonshot's endpoint speaks the same
 * OpenAI chat-completions wire protocol [OpenAILLMClient] already
 * implements, it is not a mislabel of the actual model.
 */
private val KIMI_K2_MODEL = LLModel(
    provider = LLMProvider.OpenAI,
    id = "kimi-k2.6",
    capabilities = listOf(
        LLMCapability.Temperature,
        LLMCapability.Tools,
        LLMCapability.ToolChoice,
        LLMCapability.Schema.JSON.Basic,
    ),
    contextLength = 262_144,
    maxOutputTokens = 262_144,
)

/**
 * Credentials for the provider a conversation is bound to (KD5): [apiKey]
 * covers every provider but Ollama. [baseUrl] means different things per
 * provider - Ollama's rider-reachable server (R11 - no on-device
 * inference, required), or an optional override of an OpenAI-protocol
 * client's default endpoint (OpenAI itself, or Kimi/Moonshot) so a rider
 * can point at a compatible proxy/self-host or Moonshot's China-region
 * endpoint without a dedicated provider entry per endpoint.
 */
data class ProviderCredentials(
    val apiKey: String? = null,
    val baseUrl: String? = null,
)

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
            systemPrompt = SystemPrompt.CONTENT,
            onToolCallStarting = onToolCallStarting,
        )
    }

    private fun resolveExecutorAndModel(
        providerId: ProviderId,
        credentials: ProviderCredentials,
    ): Pair<PromptExecutor, LLModel> = when (providerId) {
        ProviderId.ANTHROPIC -> {
            val apiKey = requireApiKey(providerId, credentials)
            MultiLLMPromptExecutor(
                AnthropicLLMClient(apiKey = apiKey, httpClientFactory = koogHttpClientFactory),
            ) to AnthropicModels.Opus_4_1
        }
        ProviderId.OPENAI -> {
            val apiKey = requireApiKey(providerId, credentials)
            val settings = credentials.baseUrl?.takeIf { it.isNotBlank() }
                ?.let { OpenAIClientSettings(baseUrl = it) }
                ?: OpenAIClientSettings()
            MultiLLMPromptExecutor(
                OpenAILLMClient(apiKey = apiKey, settings = settings, httpClientFactory = koogHttpClientFactory),
            ) to OpenAIModels.Chat.GPT4o
        }
        ProviderId.GOOGLE -> {
            val apiKey = requireApiKey(providerId, credentials)
            MultiLLMPromptExecutor(
                GoogleLLMClient(apiKey = apiKey, httpClientFactory = koogHttpClientFactory),
            ) to GoogleModels.Gemini2_5Pro
        }
        ProviderId.DEEPSEEK -> {
            val apiKey = requireApiKey(providerId, credentials)
            MultiLLMPromptExecutor(
                DeepSeekLLMClient(apiKey = apiKey, httpClientFactory = koogHttpClientFactory),
            ) to DeepSeekModels.DeepSeekV4Flash
        }
        ProviderId.OPENROUTER -> {
            val apiKey = requireApiKey(providerId, credentials)
            MultiLLMPromptExecutor(
                OpenRouterLLMClient(apiKey = apiKey, httpClientFactory = koogHttpClientFactory),
            ) to OpenRouterModels.GPT4o
        }
        ProviderId.OLLAMA -> {
            val baseUrl = credentials.baseUrl?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Ollama requires a base URL.")
            MultiLLMPromptExecutor(
                OllamaClient(httpClientFactory = koogHttpClientFactory, baseUrl = baseUrl),
            ) to OllamaModels.Meta.LLAMA_3_2
        }
        ProviderId.KIMI -> {
            // Moonshot's own platform speaks the OpenAI chat-completions
            // protocol (confirmed against platform.kimi.ai's docs), so the
            // OpenAI client works unmodified pointed at its base URL - Koog
            // has no first-class Kimi client/model catalog, hence the
            // hand-built LLModel below (LLMProvider.OpenAI is the correct
            // capability/request-shaping profile, not a mislabel).
            val apiKey = requireApiKey(providerId, credentials)
            val baseUrl = credentials.baseUrl?.takeIf { it.isNotBlank() } ?: KIMI_DEFAULT_BASE_URL
            MultiLLMPromptExecutor(
                OpenAILLMClient(
                    apiKey = apiKey,
                    settings = OpenAIClientSettings(baseUrl = baseUrl),
                    httpClientFactory = koogHttpClientFactory,
                ),
            ) to KIMI_K2_MODEL
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
