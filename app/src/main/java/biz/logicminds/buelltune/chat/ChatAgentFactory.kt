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
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.modelsById
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
 * The capability set assumed for a model id Koog's static catalog has no
 * entry for - Kimi's flagship default below, and any rider-picked id
 * [assumedCapableModel] builds for a custom OpenAI-compatible base URL,
 * Kimi's own live model list, or an Ollama model pulled locally (see
 * [listModels]/[resolveModel]). [LLMCapability.Tools] is required for
 * [EcmTools] to work at all.
 */
private val ASSUMED_CHAT_CAPABILITIES = listOf(
    LLMCapability.Temperature,
    LLMCapability.Tools,
    LLMCapability.ToolChoice,
    LLMCapability.Schema.JSON.Basic,
)

/**
 * Koog 1.2.0 has no first-class Kimi/Moonshot client or model catalog
 * (unlike OpenAI, Anthropic, etc.), so this is a hand-built [LLModel] for
 * Moonshot's current flagship chat-completions model id, `kimi-k2.6`
 * (confirmed against platform.kimi.ai's own Chat Completions API docs,
 * which document Tool Use/function calling support). [LLMProvider.OpenAI]
 * is deliberately reused as the capability/request-shaping profile:
 * Moonshot's endpoint speaks the same OpenAI chat-completions wire
 * protocol [OpenAILLMClient] already implements, it is not a mislabel of
 * the actual model.
 */
private val KIMI_DEFAULT_MODEL = LLModel(
    provider = LLMProvider.OpenAI,
    id = "kimi-k2.6",
    capabilities = ASSUMED_CHAT_CAPABILITIES,
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
 * Resolves a Koog [PromptExecutor]/[LLModel] pair for a conversation and
 * binds it, plus [EcmTools]' read-only tool registry, into a [ChatAgent] -
 * plus [listModels], the live "what can I actually pick" query behind the
 * new-conversation model picker (R21). This is the only place a real,
 * network-calling [PromptExecutor]/[LLMClient] gets constructed (KTD6) -
 * [ChatAgent] itself takes a [PromptExecutor] as a plain constructor
 * parameter, so a test double stands in without touching this class.
 */
class ChatAgentFactory {

    fun create(
        providerId: ProviderId,
        credentials: ProviderCredentials,
        modelId: String?,
        ecmTools: EcmTools,
        onToolCallStarting: (String) -> Unit = {},
    ): ChatAgent {
        val client = buildClient(providerId, credentials)
        return ChatAgent(
            promptExecutor = MultiLLMPromptExecutor(client),
            llmModel = resolveModel(providerId, modelId),
            toolRegistry = ecmToolRegistry(ecmTools),
            systemPrompt = SystemPrompt.CONTENT,
            onToolCallStarting = onToolCallStarting,
        )
    }

    /**
     * Queries [providerId]'s live model list (R21) - a real network call,
     * run this off the main thread - so the new-conversation picker can
     * offer exactly what that account/server currently has, rather than
     * one fixed per-provider choice. Filtered to models Koog's own static
     * catalog ([AnthropicModels] etc., via [modelsById]) marks as
     * tool-calling capable, or models absent from that catalog entirely
     * (Kimi - Koog has none at all; a custom OpenAI-compatible base URL,
     * whose ids won't match OpenAI's own catalog; or an Ollama model a
     * rider has pulled locally) - capability info for those genuinely
     * isn't knowable without a real chat turn, so they're included rather
     * than hidden. [resolveModel] makes the identical assumption explicit
     * again at chat-creation time via [assumedCapableModel], so a model
     * this method offered is always actually usable.
     *
     * Ollama's [LLMClient] (unlike every other provider here) does not
     * override Koog's `models()` - [OllamaClient.getModels] is the real
     * equivalent, called directly instead.
     */
    suspend fun listModels(providerId: ProviderId, credentials: ProviderCredentials): List<LLModel> {
        val client = buildClient(providerId, credentials)
        val models = if (client is OllamaClient) {
            client.getModels().map { card -> LLModel(provider = LLMProvider.Ollama, id = card.name) }
        } else {
            client.models()
        }
        return models
            .filter { it.capabilities == null || it.supports(LLMCapability.Tools) }
            .sortedBy { it.id }
    }

    /**
     * `modelId` blank/`"default"` covers every conversation persisted
     * before R21 (the literal string [ChatFragment] used to always write)
     * - falls back to the same fixed-per-provider model this class always
     * used, so old conversations keep working unchanged. A real, rider-picked
     * id is looked up in Koog's static catalog first (rich capabilities,
     * context length, etc. from [AnthropicModels]/[OpenAIModels]/etc.);
     * absent from it, [assumedCapableModel] builds a bare [LLModel] instead
     * - never a hard failure, since [listModels] already only offered ids
     * it had reason to believe were usable.
     *
     * `internal` (not `private`) purely so [TestChatAgentFactory] can
     * exercise this pure, network-free selection logic directly - the
     * single most important correctness property this method has
     * ([EcmTools] silently breaking for a rider-picked, uncatalogued model
     * id is a real regression risk), and it's the one part of R21 no
     * live-provider smoke test in this sandbox can observe without a real
     * API key/network.
     */
    internal fun resolveModel(providerId: ProviderId, modelId: String?): LLModel {
        if (modelId.isNullOrBlank() || modelId == "default") {
            return defaultModel(providerId)
        }
        return catalogedModel(providerId, modelId) ?: assumedCapableModel(providerId, modelId)
    }

    private fun defaultModel(providerId: ProviderId): LLModel = when (providerId) {
        ProviderId.ANTHROPIC -> AnthropicModels.Opus_4_1
        ProviderId.OPENAI -> OpenAIModels.Chat.GPT4o
        ProviderId.GOOGLE -> GoogleModels.Gemini2_5Pro
        ProviderId.DEEPSEEK -> DeepSeekModels.DeepSeekV4Flash
        ProviderId.OPENROUTER -> OpenRouterModels.GPT4o
        ProviderId.OLLAMA -> OllamaModels.Meta.LLAMA_3_2
        ProviderId.KIMI -> KIMI_DEFAULT_MODEL
    }

    private fun catalogedModel(providerId: ProviderId, modelId: String): LLModel? = when (providerId) {
        ProviderId.ANTHROPIC -> AnthropicModels.modelsById()[modelId]
        ProviderId.OPENAI -> OpenAIModels.modelsById()[modelId]
        ProviderId.GOOGLE -> GoogleModels.modelsById()[modelId]
        ProviderId.DEEPSEEK -> DeepSeekModels.modelsById()[modelId]
        ProviderId.OPENROUTER -> OpenRouterModels.modelsById()[modelId]
        ProviderId.OLLAMA -> OllamaModels.modelsById()[modelId]
        ProviderId.KIMI -> null // Koog has no Kimi/Moonshot catalog at all (see KIMI_DEFAULT_MODEL's KDoc)
    }

    internal fun assumedCapableModel(providerId: ProviderId, modelId: String): LLModel =
        LLModel(provider = llmProviderFor(providerId), id = modelId, capabilities = ASSUMED_CHAT_CAPABILITIES)

    private fun llmProviderFor(providerId: ProviderId): LLMProvider = when (providerId) {
        ProviderId.ANTHROPIC -> LLMProvider.Anthropic
        ProviderId.OPENAI -> LLMProvider.OpenAI
        ProviderId.GOOGLE -> LLMProvider.Google
        ProviderId.DEEPSEEK -> LLMProvider.DeepSeek
        ProviderId.OPENROUTER -> LLMProvider.OpenRouter
        ProviderId.OLLAMA -> LLMProvider.Ollama
        ProviderId.KIMI -> LLMProvider.OpenAI // matches KIMI_DEFAULT_MODEL's own provider tag - Moonshot speaks the OpenAI wire protocol
    }

    private fun buildClient(providerId: ProviderId, credentials: ProviderCredentials): LLMClient = when (providerId) {
        ProviderId.ANTHROPIC -> {
            val apiKey = requireApiKey(providerId, credentials)
            AnthropicLLMClient(apiKey = apiKey, httpClientFactory = koogHttpClientFactory)
        }
        ProviderId.OPENAI -> {
            val apiKey = requireApiKey(providerId, credentials)
            val settings = credentials.baseUrl?.takeIf { it.isNotBlank() }
                ?.let { OpenAIClientSettings(baseUrl = it) }
                ?: OpenAIClientSettings()
            OpenAILLMClient(apiKey = apiKey, settings = settings, httpClientFactory = koogHttpClientFactory)
        }
        ProviderId.GOOGLE -> {
            val apiKey = requireApiKey(providerId, credentials)
            GoogleLLMClient(apiKey = apiKey, httpClientFactory = koogHttpClientFactory)
        }
        ProviderId.DEEPSEEK -> {
            val apiKey = requireApiKey(providerId, credentials)
            DeepSeekLLMClient(apiKey = apiKey, httpClientFactory = koogHttpClientFactory)
        }
        ProviderId.OPENROUTER -> {
            val apiKey = requireApiKey(providerId, credentials)
            OpenRouterLLMClient(apiKey = apiKey, httpClientFactory = koogHttpClientFactory)
        }
        ProviderId.OLLAMA -> {
            val baseUrl = credentials.baseUrl?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Ollama requires a base URL.")
            OllamaClient(httpClientFactory = koogHttpClientFactory, baseUrl = baseUrl)
        }
        ProviderId.KIMI -> {
            // Moonshot's own platform speaks the OpenAI chat-completions
            // protocol (confirmed against platform.kimi.ai's docs), so the
            // OpenAI client works unmodified pointed at its base URL.
            val apiKey = requireApiKey(providerId, credentials)
            val baseUrl = credentials.baseUrl?.takeIf { it.isNotBlank() } ?: KIMI_DEFAULT_BASE_URL
            OpenAILLMClient(
                apiKey = apiKey,
                settings = OpenAIClientSettings(baseUrl = baseUrl),
                httpClientFactory = koogHttpClientFactory,
            )
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
