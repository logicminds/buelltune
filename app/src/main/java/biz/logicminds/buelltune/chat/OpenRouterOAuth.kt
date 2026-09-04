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

import android.net.Uri
import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.security.MessageDigest
import java.security.SecureRandom
import org.json.JSONObject

/**
 * OpenRouter's OAuth PKCE flow (https://openrouter.ai/docs/guides/overview/auth/oauth):
 * OpenRouter is the only configured provider offering a sanctioned,
 * documented third-party OAuth path. Anthropic's Free/Pro/Max OAuth tokens
 * are explicitly ToS-restricted to Claude.ai/Claude Code as of its 2026
 * "Legal and Compliance" policy (verified against Anthropic's own docs) -
 * using them here would violate that policy, so every other provider stays
 * token-entry only, matching [ProviderCredentials]' `apiKey` field.
 *
 * The flow needs no pre-registered client ID/secret (PKCE for a public
 * client): a random [codeVerifier] is generated locally, its SHA-256 hash
 * sent as the `code_challenge` to start the flow, then the same
 * [codeVerifier] proves ownership when the resulting `code` is exchanged
 * for a user-controlled API key.
 */
object OpenRouterOAuth {
    /** Custom-scheme deep link [LlmSettingsActivity]'s manifest intent-filter matches. */
    const val CALLBACK_URL = "biz.logicminds.buelltune://oauth/openrouter"

    private const val AUTH_URL = "https://openrouter.ai/auth"
    private const val KEY_EXCHANGE_URL = "https://openrouter.ai/api/v1/auth/keys"

    /** A random, high-entropy PKCE code verifier (32 bytes, base64url-encoded -> 43 chars, within the 43-128 char spec range). */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    /** The S256 `code_challenge` OpenRouter's `/auth` endpoint expects for [codeVerifier]. */
    fun codeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return base64Url(digest)
    }

    /** The browser URL to send the rider to, starting the PKCE flow for [codeChallenge]. */
    fun buildAuthUrl(codeChallenge: String): String =
        Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("callback_url", CALLBACK_URL)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()

    /**
     * Exchanges an authorization [code] (from the `code` query param on
     * [CALLBACK_URL]'s redirect) plus the original [codeVerifier] for a
     * user-controlled OpenRouter API key. Throws [IllegalStateException]
     * with OpenRouter's own error text on a non-2xx response (expired
     * code, mismatched verifier, etc. - see the OAuth PKCE doc's Error
     * Codes section).
     */
    suspend fun exchangeCodeForKey(code: String, codeVerifier: String): String {
        val client = HttpClient(OkHttp)
        try {
            val response = client.post(KEY_EXCHANGE_URL) {
                contentType(ContentType.Application.Json)
                setBody(
                    JSONObject()
                        .put("code", code)
                        .put("code_verifier", codeVerifier)
                        .put("code_challenge_method", "S256")
                        .toString(),
                )
            }
            val bodyText = response.bodyAsText()
            if (response.status.value !in 200..299) {
                throw IllegalStateException("OpenRouter sign-in failed (${response.status.value}): $bodyText")
            }
            return JSONObject(bodyText).getString("key")
        } finally {
            client.close()
        }
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
