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

/**
 * The LLM providers the chat agent can be configured against (KD4): every
 * provider is available from v1, not "Anthropic first, others added later."
 *
 * [KIMI] talks to Moonshot AI's own OpenAI-protocol-compatible endpoint
 * (`https://api.moonshot.ai/v1` by default, overridable) directly with a
 * Moonshot API key - it no longer piggybacks on [OPENROUTER] the way an
 * earlier revision of this enum did; Moonshot's own platform is the
 * lower-latency, non-proxied path and lets a rider use a Kimi-specific key
 * instead of an OpenRouter account.
 *
 * [KIMI_CODE] is a separate product from [KIMI]: Moonshot's subscription
 * coding-agent plan (`https://www.kimi.com/code`), billed and entitled
 * independently of the pay-per-token Moonshot Open Platform [KIMI] talks
 * to, reached at a different base URL
 * (`https://api.kimi.com/coding/v1`) with different model ids (`k3`,
 * `k3-256k`, `kimi-for-coding`, `kimi-for-coding-highspeed`). Riders
 * generate its API key from the Kimi Code Console
 * (`https://www.kimi.com/code/console`) - Moonshot's own docs
 * (`kimi.com/code/docs/en/kimi-code/models.html`) document exactly this
 * static-API-key third-party-tool integration path generically ("Create
 * an API Key in the Kimi Code Console... fill in the Base URL and Model
 * ID in your tool"), separately from Kimi Code's OAuth device-flow login
 * (deliberately not implemented here - that path gates on a server-side
 * allowlist of recognized coding-agent client identities, confirmed via
 * real third-party breakage reports, a materially different risk profile
 * from a static, self-provisioned API key hitting a documented endpoint).
 *
 * AWS Bedrock is deliberately excluded (KD4 correction, U5): Koog 1.2.0's
 * `prompt-executor-bedrock-client-android` artifact compiles to a single
 * internal `Stub` class on the Android target - no `BedrockLLMClient`,
 * `BedrockModels`, or credential provider types exist to construct
 * (verified by decompiling the resolved dependency). Bedrock's JVM-only AWS
 * SDK client isn't ported to Koog's Android target as of this version;
 * revisit if a later Koog release adds it, or a future unit hand-rolls an
 * AWS SigV4 `LLMClient` directly (out of proportion for this feature).
 */
enum class ProviderId {
    ANTHROPIC,
    OPENAI,
    GOOGLE,
    DEEPSEEK,
    OPENROUTER,
    OLLAMA,
    KIMI,
    KIMI_CODE,
}
