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
 * [EcmTools] to work at all. The other three flags are each a real bug
 * this project hit against a real device (a hand-built [LLModel] missing
 * one of them isn't a degraded/best-effort model, it's one Koog's own
 * clients hard-refuse to send a request for at all), not defensive
 * padding:
 * - [LLMCapability.Completion] is the base "this model can generate at
 *   all" flag every real catalog entry across every Koog provider client
 *   carries (confirmed by inspecting [AnthropicModels]/[OpenAIModels]/
 *   [GoogleModels]/etc. directly - none omit it). Every OpenAI-family
 *   client `require(model.supports(LLMCapability.Completion))`s it before
 *   sending anything; missing it, [OpenAILLMClient] threw "Model
 *   kimi-for-coding does not support completion" on a real device call.
 * - [LLMCapability.OpenAIEndpoint.Completions] is how
 *   [OpenAILLMClient.determineParams] decides whether to shape a request
 *   for the classic `/chat/completions` wire format or the newer
 *   `/responses` one - missing both this and
 *   [LLMCapability.OpenAIEndpoint.Responses], it hard-throws
 *   `LLMClientException("Cannot determine proper LLM params for OpenAI
 *   model: $id")`, also confirmed on a real device call.
 *
 * Every model this set backs (Kimi, Kimi Code, a rider's custom
 * OpenAI-compatible endpoint) speaks the classic chat-completions
 * protocol, confirmed against each provider's own docs. Non-OpenAI
 * clients ([AnthropicLLMClient] etc., via [llmProviderFor]) simply ignore
 * [LLMCapability.OpenAIEndpoint.Completions] - it's OpenAI-client-
 * specific, not a claim about what the underlying provider itself
 * supports; [LLMCapability.Completion] is the one flag every client here
 * actually requires.
 */
private val ASSUMED_CHAT_CAPABILITIES = listOf(
    LLMCapability.Temperature,
    LLMCapability.Tools,
    LLMCapability.ToolChoice,
    LLMCapability.Schema.JSON.Basic,
    LLMCapability.Completion,
    LLMCapability.OpenAIEndpoint.Completions,
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

/** Kimi Code's documented OpenAI-compatible base URL (kimi.com/code/docs/en/ - "API Access" / "Service Endpoint" table). Distinct product/billing from [KIMI_DEFAULT_BASE_URL]. */
private const val KIMI_CODE_DEFAULT_BASE_URL = "https://api.kimi.com/coding/v1"

/**
 * Kimi Code's four model ids, hand-built the same way [KIMI_DEFAULT_MODEL]
 * is - Koog has no catalog for these either. Context windows and default
 * model id sourced from Moonshot's own docs (kimi.com/code/docs/en/,
 * "Model IDs" table): `k3` is the flagship, up to 1M context on higher
 * membership tiers (Koog's [LLModel] has no per-tier concept, so this
 * uses the ceiling value - a lower-tier account gets a normal over-quota
 * error from Kimi Code itself, not a client-side failure); `k3-256k` is
 * the fixed-256K version of the same model at roughly half the quota
 * cost; `kimi-for-coding` (K2.7 Code) is available to every membership
 * tier - the safe default; `kimi-for-coding-highspeed` needs an
 * Allegretto-or-above plan. This is also the *complete* model catalog for
 * this provider, not just a fallback: Kimi Code has no models-list
 * endpoint at all (confirmed both by Moonshot's own docs, which document
 * chat completions and this fixed table but nothing else, and by a real
 * device call to `{KIMI_CODE_DEFAULT_BASE_URL}/models` returning a
 * genuine `resource_not_found_error` 404, not an auth failure) - see
 * [listModels]'s early-return for this provider.
 */
private val KIMI_CODE_MODELS = listOf(
    LLModel(provider = LLMProvider.OpenAI, id = "kimi-for-coding", capabilities = ASSUMED_CHAT_CAPABILITIES, contextLength = 262_144, maxOutputTokens = 262_144),
    LLModel(provider = LLMProvider.OpenAI, id = "kimi-for-coding-highspeed", capabilities = ASSUMED_CHAT_CAPABILITIES, contextLength = 262_144, maxOutputTokens = 262_144),
    LLModel(provider = LLMProvider.OpenAI, id = "k3-256k", capabilities = ASSUMED_CHAT_CAPABILITIES, contextLength = 262_144, maxOutputTokens = 262_144),
    LLModel(provider = LLMProvider.OpenAI, id = "k3", capabilities = ASSUMED_CHAT_CAPABILITIES, contextLength = 1_048_576, maxOutputTokens = 262_144),
)

private val KIMI_CODE_DEFAULT_MODEL = KIMI_CODE_MODELS.first { it.id == "kimi-for-coding" }

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
     * Two providers skip the network call entirely and return a static
     * list instead of querying `client.models()`: [ProviderId.KIMI_CODE]
     * has no models-list endpoint at all (see [KIMI_CODE_MODELS]'s KDoc),
     * so [KIMI_CODE_MODELS] itself *is* the live list; Ollama's
     * [LLMClient] does not override Koog's `models()` -
     * [OllamaClient.getModels] is the real equivalent, called directly
     * instead.
     */
    suspend fun listModels(providerId: ProviderId, credentials: ProviderCredentials): List<LLModel> {
        if (providerId == ProviderId.KIMI_CODE) {
            return KIMI_CODE_MODELS
        }
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
        ProviderId.KIMI_CODE -> KIMI_CODE_DEFAULT_MODEL
    }

    private fun catalogedModel(providerId: ProviderId, modelId: String): LLModel? = when (providerId) {
        ProviderId.ANTHROPIC -> AnthropicModels.modelsById()[modelId]
        ProviderId.OPENAI -> OpenAIModels.modelsById()[modelId]
        ProviderId.GOOGLE -> GoogleModels.modelsById()[modelId]
        ProviderId.DEEPSEEK -> DeepSeekModels.modelsById()[modelId]
        ProviderId.OPENROUTER -> OpenRouterModels.modelsById()[modelId]
        ProviderId.OLLAMA -> OllamaModels.modelsById()[modelId]
        ProviderId.KIMI -> null // Koog has no Kimi/Moonshot catalog at all (see KIMI_DEFAULT_MODEL's KDoc)
        ProviderId.KIMI_CODE -> KIMI_CODE_MODELS.firstOrNull { it.id == modelId }
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
        ProviderId.KIMI_CODE -> LLMProvider.OpenAI // same reasoning - Kimi Code's endpoint is OpenAI-protocol-compatible too
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
            // OpenAI client works unmodified pointed at its base URL. Both
            // KIMI_DEFAULT_BASE_URL and any rider-typed override already
            // include the trailing `/v1` segment - that's how Moonshot's
            // own docs present "Base URL" verbatim, and llm_kimi_base_url_
            // summary tells the rider the same thing. OpenAIClientSettings'
            // *default* chatCompletionsPath/modelsPath
            // ("v1/chat/completions"/"v1/models") assume a bare host
            // instead and re-add that same segment - unoverridden, this
            // doubles to a genuine `/v1/v1/...` request path and 404s
            // (confirmed on a real device against Kimi Code's identical
            // base-URL shape below, before this fix). Overriding both to
            // drop the redundant `v1/` prefix is what actually makes this
            // base URL (or a rider's own, following the same documented
            // convention) resolve correctly.
            val apiKey = requireApiKey(providerId, credentials)
            val baseUrl = credentials.baseUrl?.takeIf { it.isNotBlank() } ?: KIMI_DEFAULT_BASE_URL
            OpenAILLMClient(
                apiKey = apiKey,
                settings = OpenAIClientSettings(
                    baseUrl = baseUrl,
                    chatCompletionsPath = "chat/completions",
                    modelsPath = "models",
                ),
                httpClientFactory = koogHttpClientFactory,
            )
        }
        ProviderId.KIMI_CODE -> {
            // Kimi Code's own docs (kimi.com/code/docs/en/ - "API Access" /
            // "Service Endpoint" table) document two protocols against this
            // product: OpenAI-compatible at this base URL
            // (KIMI_CODE_DEFAULT_BASE_URL, .../chat/completions) or
            // Anthropic-compatible at a sibling base URL (.../v1/messages).
            // This client speaks the OpenAI-compatible one. Create a key in
            // the Kimi Code Console (kimi.com/code/console) - a separate
            // credential/base URL from the plain KIMI branch above:
            // different product, different billing, different endpoint.
            //
            // chatCompletionsPath is overridden for the same reason as the
            // KIMI branch above - KIMI_CODE_DEFAULT_BASE_URL already ends
            // in `/v1` (Kimi Code's own documented "Base URL" string), and
            // leaving OpenAIClientSettings' default `chatCompletionsPath`
            // ("v1/chat/completions") in place doubles it to a real
            // `/coding/v1/v1/chat/completions` request - confirmed on a
            // real device: the exact 404 `resource_not_found_error` this
            // project hit sending a genuine chat message through this
            // provider before this fix, not the earlier (separate, already
            // fixed) `/models`-listing 404. modelsPath is left at its
            // default since this provider never calls it - see
            // [listModels]'s early-return for [ProviderId.KIMI_CODE].
            val apiKey = requireApiKey(providerId, credentials)
            val baseUrl = credentials.baseUrl?.takeIf { it.isNotBlank() } ?: KIMI_CODE_DEFAULT_BASE_URL
            OpenAILLMClient(
                apiKey = apiKey,
                settings = OpenAIClientSettings(
                    baseUrl = baseUrl,
                    chatCompletionsPath = "chat/completions",
                ),
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
