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

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure, network-free coverage for [ChatAgentFactory.resolveModel] (R21) -
 * the [ai.koog.prompt.llm.LLModel] a conversation's stored
 * providerId/modelId actually resolves to at chat-creation time. No test
 * here calls a real provider API or opens a network connection:
 * [ChatAgentFactory]'s client-construction/`listModels` half (the
 * network-calling side) has no coverage here or anywhere else in this
 * codebase for every provider except [ProviderId.KIMI_CODE] - an accepted
 * v1 gap, confirmed by code review. `listModels(KIMI_CODE, ...)` is the
 * one exception: it never builds a client or opens a connection (Kimi
 * Code has no models-list endpoint at all, confirmed against a real
 * device - see [ChatAgentFactory]'s KDoc), so it's exercised directly
 * below alongside the rest of this class's selection-logic tests.
 */
class TestChatAgentFactory {

    private val factory = ChatAgentFactory()

    @Test
    fun blankOrDefaultModelId_resolvesToTheFixedPerProviderDefault() {
        assertEquals(AnthropicModels.Opus_4_1, factory.resolveModel(ProviderId.ANTHROPIC, null))
        assertEquals(AnthropicModels.Opus_4_1, factory.resolveModel(ProviderId.ANTHROPIC, ""))
        assertEquals(AnthropicModels.Opus_4_1, factory.resolveModel(ProviderId.ANTHROPIC, "default"))
        assertEquals(OpenAIModels.Chat.GPT4o, factory.resolveModel(ProviderId.OPENAI, "default"))
    }

    @Test
    fun realCataloguedModelId_resolvesToTheRichCatalogEntry_notABareFallback() {
        // AnthropicModels.Opus_4_1.id is the real id Koog's own static
        // catalog keys AnthropicLLMClient.models() responses against -
        // looking it up here proves the same lookup ChatAgentFactory.create
        // performs at chat time, not just at listModels() time.
        val resolved = factory.resolveModel(ProviderId.ANTHROPIC, AnthropicModels.Opus_4_1.id)
        assertEquals(AnthropicModels.Opus_4_1, resolved)
        assertTrue(resolved.supports(LLMCapability.Tools))
    }

    @Test
    fun uncataloguedModelId_stillResolvesAsToolCapable() {
        // A rider-picked id from a custom OpenAI-compatible base URL, or
        // from Kimi's own live model list - Koog's static catalog has no
        // entry for either, so resolveModel must not silently strip tool
        // support: AnthropicLLMClient/OpenAILLMClient.execute() hard-gate
        // on model.supports(LLMCapability.Tools) before sending any tool
        // to the provider (confirmed by inspecting both clients directly -
        // see ChatAgentFactory's KDoc), so a model missing that capability
        // would break EcmTools entirely, not just degrade gracefully.
        val resolved = factory.resolveModel(ProviderId.OPENAI, "some-self-hosted-proxy-model-id")
        assertEquals("some-self-hosted-proxy-model-id", resolved.id)
        assertEquals(LLMProvider.OpenAI, resolved.provider)
        assertTrue(resolved.supports(LLMCapability.Tools))
    }

    @Test
    fun kimi_alwaysUncatalogued_resolvesAsToolCapable() {
        // Koog 1.2.0 ships no Kimi/Moonshot catalog at all, so every real
        // Kimi model id (not just uncommon ones) takes this path.
        val resolved = factory.resolveModel(ProviderId.KIMI, "kimi-k2.6")
        assertEquals("kimi-k2.6", resolved.id)
        assertTrue(resolved.supports(LLMCapability.Tools))
    }

    @Test
    fun kimiCode_defaultModelId_resolvesToKimiForCoding() {
        // "kimi-for-coding" (K2.7 Code) is the only Kimi Code model
        // available on every membership tier (Moonshot's own Model
        // Configuration doc) - the safe default when no modelId is picked.
        val resolved = factory.resolveModel(ProviderId.KIMI_CODE, "default")
        assertEquals("kimi-for-coding", resolved.id)
        assertTrue(resolved.supports(LLMCapability.Tools))
    }

    @Test
    fun kimiCode_listModels_returnsItsStaticCatalog_noNetworkCall() = runBlocking {
        // The one listModels branch this suite can exercise directly: Kimi
        // Code has no models-list endpoint (real device call to
        // .../coding/v1/models returned a genuine 404), so this must never
        // attempt to build a client or reach the network - it just returns
        // the same hand-built catalog resolveModel already draws from.
        val models = factory.listModels(ProviderId.KIMI_CODE, ProviderCredentials(apiKey = null))
        assertEquals(
            setOf("kimi-for-coding", "kimi-for-coding-highspeed", "k3-256k", "k3"),
            models.map { it.id }.toSet(),
        )
        assertTrue(models.all { it.supports(LLMCapability.Tools) })
    }

    @Test
    fun kimiCode_allFourRealModelIds_resolveFromItsHandBuiltCatalog() {
        // Kimi Code is a separate product/catalog from plain Kimi
        // (ProviderId.KIMI) - distinct model ids, hand-built the same way
        // since Koog has no catalog for either. Confirms every documented
        // Kimi Code model id (kimi.com/code/docs/en/kimi-code/models.html)
        // resolves to itself, tool-capable, rather than silently falling
        // through to assumedCapableModel's generic path.
        for (modelId in listOf("kimi-for-coding", "kimi-for-coding-highspeed", "k3-256k", "k3")) {
            val resolved = factory.resolveModel(ProviderId.KIMI_CODE, modelId)
            assertEquals(modelId, resolved.id)
            assertTrue("$modelId should be tool-capable", resolved.supports(LLMCapability.Tools))
        }
        // k3's 1M context is the one real differentiator worth pinning.
        assertEquals(1_048_576L, factory.resolveModel(ProviderId.KIMI_CODE, "k3").contextLength)
    }

    @Test
    fun assumedCapableModel_matchesEachProvidersRealLLMProviderTag() {
        // This mapping must exactly match what each provider's real
        // LLMClient reports via llmProvider() - a mismatch would make
        // MultiLLMPromptExecutor.clientFor(effectiveModel) fail to find the
        // right client for a hand-built LLModel at send time.
        assertEquals(LLMProvider.Anthropic, factory.assumedCapableModel(ProviderId.ANTHROPIC, "x").provider)
        assertEquals(LLMProvider.OpenAI, factory.assumedCapableModel(ProviderId.OPENAI, "x").provider)
        assertEquals(LLMProvider.Google, factory.assumedCapableModel(ProviderId.GOOGLE, "x").provider)
        assertEquals(LLMProvider.DeepSeek, factory.assumedCapableModel(ProviderId.DEEPSEEK, "x").provider)
        assertEquals(LLMProvider.OpenRouter, factory.assumedCapableModel(ProviderId.OPENROUTER, "x").provider)
        assertEquals(LLMProvider.Ollama, factory.assumedCapableModel(ProviderId.OLLAMA, "x").provider)
        assertEquals(LLMProvider.OpenAI, factory.assumedCapableModel(ProviderId.KIMI, "x").provider)
        assertEquals(LLMProvider.OpenAI, factory.assumedCapableModel(ProviderId.KIMI_CODE, "x").provider)
    }
}
