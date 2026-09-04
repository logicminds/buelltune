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
 * codebase - an accepted v1 gap, confirmed by code review. This file only
 * proves the selection logic downstream of a model id, whether that id
 * came from a live `listModels` result or (for legacy rows) the constant
 * literal `"default"`.
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
    }
}
