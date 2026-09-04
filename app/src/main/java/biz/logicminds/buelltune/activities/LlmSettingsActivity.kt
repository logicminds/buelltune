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
package biz.logicminds.buelltune.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import biz.logicminds.buelltune.AppContainer
import biz.logicminds.buelltune.AppPreferences
import biz.logicminds.buelltune.R
import biz.logicminds.buelltune.chat.OpenRouterOAuth
import biz.logicminds.buelltune.chat.ProviderCredentials
import biz.logicminds.buelltune.chat.ProviderId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * LLM provider credential entry (R9, R11, R14; KD4; KTD6).
 *
 * A modern, non-deprecated replacement path for chat provider settings:
 * unlike `PrefsActivity` (`android.preference.PreferenceActivity`), this
 * hosts an `androidx.preference.PreferenceFragmentCompat` built from
 * `res/xml/llm_prefs.xml`. It persists to the exact same
 * `PreferenceManager.getDefaultSharedPreferences` store `app_prefs.xml`
 * already uses (see `AppPreferences.defaultPrefs`), so no second storage
 * mechanism is introduced -- only a modern authoring widget, per KTD6.
 *
 * Reached via `Intent` from Chat's setup prompt (R14, shown when zero
 * providers are configured) and from the app's Settings screen
 * (`PrefsActivity`'s "AI Chat Providers" row, `app_prefs.xml`'s
 * `ai_chat_category`) - Settings is the discoverable, always-available
 * path for adding, editing, or blanking out a provider's key after
 * first-run setup, matching where riders already look for app-wide
 * configuration. It has no `main_drawer.xml` entry of its own.
 *
 * Also the redirect target for [OpenRouterOAuth]'s PKCE flow
 * ([OpenRouterOAuth.CALLBACK_URL], wired in the manifest as an
 * `android:launchMode="singleTask"` deep link): every other provider is
 * token-entry only (see [OpenRouterOAuth]'s KDoc for why OAuth isn't
 * offered more broadly).
 */
class LlmSettingsActivity : AppCompatActivity() {

    private val oauthScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, LlmPreferenceFragment())
                .commit()
        }
        handleOAuthRedirect(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthRedirect(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        oauthScope.cancel()
    }

    /**
     * `singleTask` means a rider returning from the browser redelivers
     * into this same instance via [onNewIntent] in the common case, but a
     * cold start after the OS reclaimed the task while backgrounded lands
     * here via [onCreate] instead - both paths funnel through this one
     * handler. Non-matching intents (plain launches) no-op immediately.
     */
    private fun handleOAuthRedirect(intent: Intent) {
        val data = intent.data ?: return
        if (!data.toString().startsWith(OpenRouterOAuth.CALLBACK_URL)) return
        intent.data = null // consumed: a later re-delivery (e.g. rotation) must not re-exchange a spent code

        val code = data.getQueryParameter("code")
        val codeVerifier = AppPreferences.consumeOpenRouterOAuthVerifier(this)
        if (code.isNullOrBlank() || codeVerifier.isNullOrBlank()) {
            Toast.makeText(this, R.string.llm_openrouter_oauth_failed, Toast.LENGTH_LONG).show()
            return
        }
        oauthScope.launch {
            try {
                val apiKey = withContext(Dispatchers.IO) {
                    OpenRouterOAuth.exchangeCodeForKey(code, codeVerifier)
                }
                AppPreferences.setOpenRouterApiKey(this@LlmSettingsActivity, apiKey)
                Toast.makeText(this@LlmSettingsActivity, R.string.llm_openrouter_oauth_success, Toast.LENGTH_LONG).show()
                recreate() // reloads the PreferenceFragmentCompat so the new key's masked value shows without an app restart
            } catch (e: Exception) {
                Log.w(TAG, "OpenRouter OAuth key exchange failed", e)
                Toast.makeText(
                    this@LlmSettingsActivity,
                    getString(R.string.llm_openrouter_oauth_error, e.message),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /**
     * Hosts `res/xml/llm_prefs.xml` -- one `EditTextPreference` per provider
     * credential field, plus OpenRouter's OAuth sign-in row.
     *
     * buelltune-qoo: a key/base-URL `EditTextPreference` is otherwise just a
     * text field -- nothing here previously checked that a pasted-in value
     * was even well-formed, let alone that the provider actually accepted
     * it, so a typo'd or revoked key surfaced only much later, mid-chat, as
     * an opaque Koog exception. [bindKeyValidation] wires each credential
     * field's `OnPreferenceChangeListener` to fire a real network call --
     * [ChatRepository.listModels], the exact same "does this key actually
     * work" query [ChatFragment]'s new-conversation model picker (R21)
     * already relies on for live signal -- and reports the outcome as a
     * Toast. The listener always returns `true`: verification is
     * feedback-only and never blocks the field from saving, matching
     * [AppPreferences.credentialsFor]'s "no app restart needed" contract --
     * a slow/offline verification call must not stop the rider from typing
     * and moving on.
     *
     * [ProviderId.KIMI_CODE] is deliberately excluded: per
     * [ChatAgentFactory.listModels]'s own KDoc, that provider has no
     * models-list endpoint at all, so its call always returns the same
     * static [ChatAgentFactory.KIMI_CODE_MODELS] regardless of whether the
     * key is any good -- wiring it up here would report a fake "verified"
     * for a bad key, worse than no check at all.
     */
    class LlmPreferenceFragment : PreferenceFragmentCompat() {

        private val chatRepository by lazy { AppContainer.from(requireContext()).chatRepository }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.llm_prefs, rootKey)
            findPreference<Preference>("llm_openrouter_oauth_signin")?.setOnPreferenceClickListener {
                startOpenRouterOAuth()
                true
            }
            bindKeyValidation()
        }

        private fun startOpenRouterOAuth() {
            val context = requireContext()
            val codeVerifier = OpenRouterOAuth.generateCodeVerifier()
            AppPreferences.saveOpenRouterOAuthVerifier(context, codeVerifier)
            val authUrl = OpenRouterOAuth.buildAuthUrl(OpenRouterOAuth.codeChallenge(codeVerifier))
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
        }

        /**
         * One field per provider drives verification: the API key for every
         * key-based provider, [AppPreferences.ollamaBaseUrl] for Ollama
         * (which has no key at all, R11). A provider whose credentials span
         * two fields (OpenAI/Kimi's optional custom base URL) is verified
         * using the just-typed key plus whatever base URL is currently
         * saved -- editing the base URL alone doesn't retrigger verification,
         * a deliberate, minimal scope: base URL is an optional override, the
         * key is what "adding a provider key" (buelltune-qoo's title) means.
         */
        private fun bindKeyValidation() {
            val context = requireContext()
            bindKeyPreference("llm_anthropic_key") { key -> validate(ProviderId.ANTHROPIC, ProviderCredentials(apiKey = key)) }
            bindKeyPreference("llm_openai_key") { key ->
                validate(ProviderId.OPENAI, ProviderCredentials(apiKey = key, baseUrl = AppPreferences.openAiBaseUrl(context)))
            }
            bindKeyPreference("llm_google_key") { key -> validate(ProviderId.GOOGLE, ProviderCredentials(apiKey = key)) }
            bindKeyPreference("llm_deepseek_key") { key -> validate(ProviderId.DEEPSEEK, ProviderCredentials(apiKey = key)) }
            bindKeyPreference("llm_openrouter_key") { key -> validate(ProviderId.OPENROUTER, ProviderCredentials(apiKey = key)) }
            bindKeyPreference("llm_ollama_base_url") { baseUrl -> validate(ProviderId.OLLAMA, ProviderCredentials(baseUrl = baseUrl)) }
            bindKeyPreference("llm_kimi_key") { key ->
                validate(ProviderId.KIMI, ProviderCredentials(apiKey = key, baseUrl = AppPreferences.kimiBaseUrl(context)))
            }
        }

        /** Fires [onNonBlankValue] with the newly-typed value, skipping a blank one (the rider clearing the field to remove a credential, not adding one) -- never blocks the save either way. */
        private fun bindKeyPreference(prefKey: String, onNonBlankValue: (String) -> Unit) {
            findPreference<EditTextPreference>(prefKey)?.setOnPreferenceChangeListener { _, newValue ->
                (newValue as? String)?.takeIf { it.isNotBlank() }?.let(onNonBlankValue)
                true
            }
        }

        /** Real network call ([ChatRepository.listModels]) reporting [providerId]'s [credentials] as good, bad, or unreachable via a Toast -- see this class's KDoc. */
        private fun validate(providerId: ProviderId, credentials: ProviderCredentials) {
            val context = requireContext()
            val providerName = getString(providerCategoryStringRes(providerId))
            viewLifecycleOwner.lifecycleScope.launch {
                val result = try {
                    val models = withContext(Dispatchers.IO) { chatRepository.listModels(providerId, credentials) }
                    if (models.isNotEmpty()) Result.success(Unit) else Result.failure(IllegalStateException(getString(R.string.llm_key_verify_no_models_reason)))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Key verification failed for $providerId", e)
                    Result.failure(e)
                }
                result.fold(
                    onSuccess = {
                        Toast.makeText(context, getString(R.string.llm_key_verify_success, providerName), Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { e ->
                        val reason = e.message ?: e::class.simpleName ?: e.toString()
                        Toast.makeText(context, getString(R.string.llm_key_verify_failed, providerName, reason), Toast.LENGTH_LONG).show()
                    },
                )
            }
        }

        private fun providerCategoryStringRes(providerId: ProviderId): Int = when (providerId) {
            ProviderId.ANTHROPIC -> R.string.llm_anthropic_category
            ProviderId.OPENAI -> R.string.llm_openai_category
            ProviderId.GOOGLE -> R.string.llm_google_category
            ProviderId.DEEPSEEK -> R.string.llm_deepseek_category
            ProviderId.OPENROUTER -> R.string.llm_openrouter_category
            ProviderId.OLLAMA -> R.string.llm_ollama_category
            ProviderId.KIMI -> R.string.llm_kimi_category
            ProviderId.KIMI_CODE -> R.string.llm_kimi_code_category
        }

        companion object {
            private const val TAG = "LlmPreferenceFragment"
        }
    }

    companion object {
        private const val TAG = "LlmSettingsActivity"
    }
}
