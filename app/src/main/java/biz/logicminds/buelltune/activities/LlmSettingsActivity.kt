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
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import biz.logicminds.buelltune.AppPreferences
import biz.logicminds.buelltune.R
import biz.logicminds.buelltune.chat.OpenRouterOAuth
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

    /** Hosts `res/xml/llm_prefs.xml` -- one `EditTextPreference` per provider credential field, plus OpenRouter's OAuth sign-in row. */
    class LlmPreferenceFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.llm_prefs, rootKey)
            findPreference<Preference>("llm_openrouter_oauth_signin")?.setOnPreferenceClickListener {
                startOpenRouterOAuth()
                true
            }
        }

        private fun startOpenRouterOAuth() {
            val context = requireContext()
            val codeVerifier = OpenRouterOAuth.generateCodeVerifier()
            AppPreferences.saveOpenRouterOAuthVerifier(context, codeVerifier)
            val authUrl = OpenRouterOAuth.buildAuthUrl(OpenRouterOAuth.codeChallenge(codeVerifier))
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
        }
    }

    companion object {
        private const val TAG = "LlmSettingsActivity"
    }
}
