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
package biz.logicminds.buelltune

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import biz.logicminds.buelltune.chat.ProviderCredentials
import biz.logicminds.buelltune.chat.ProviderId

/**
 * Typed accessor for every SharedPreferences key the app reads or writes.
 *
 * Before this existed, ~12 keys were scattered across [Constants], resource
 * strings, per-file private constants, and hardcoded literals in
 * `MainActivity`, `PrefsActivity`, `DataChannelFragment`, `EEPROMFragment`,
 * `LogFragment`, `MainFragment`, `SetupFragment`, and `BurnTask`. This is a
 * pure access-pattern refactor, not a data migration: every key string below
 * is the exact literal (or `R.string`/[Constants] value) already persisted
 * on users' devices, so existing installs keep resolving their saved values
 * unchanged.
 *
 * App-wide settings (connection type, storage location, burn/EEPROM safety
 * switches, etc.) are backed by [PreferenceManager.getDefaultSharedPreferences]
 * -- the same store the `res/xml/app_prefs.xml` preference screen persists
 * to. `findPreference(...)` calls on `PrefsActivity`/`SetupFragment` that
 * merely look up a Preference UI widget by its `android:key` are left alone;
 * they're screen wiring, not a value read/write.
 *
 * Per-screen settings (data-channel picks, log interval/convert-on-save) are
 * deliberately kept in their owning [Activity]'s private preferences
 * ([Activity.getPreferences]) rather than folded into the app-wide store:
 * migrating their persistence scope would silently discard users' existing
 * per-screen values.
 */
object AppPreferences {

    // --- App-wide preferences (PreferenceManager.getDefaultSharedPreferences) ---

    private fun defaultPrefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    /** ECM connection type ("BT"/"BLE"/"COM"/"TCP"), set via the `R.string.prefs_conn_type` `ListPreference`. */
    @JvmStatic
    fun connectionType(context: Context): String =
        defaultPrefs(context).getString(context.getString(R.string.prefs_conn_type), context.getString(R.string.prefs_bt_connection))!!

    /** TCP connection host, set via the `tcp_host` `EditTextPreference`; `null` until configured. */
    @JvmStatic
    fun tcpHost(context: Context): String? = defaultPrefs(context).getString("tcp_host", null)

    /** TCP connection port as a string (caller parses it), set via the `tcp_port` `EditTextPreference`; `"0"` until configured. */
    @JvmStatic
    fun tcpPort(context: Context): String = defaultPrefs(context).getString("tcp_port", "0")!!

    /** Index into [ECM.Protocol.values] for the last-selected ECM protocol. */
    @JvmStatic
    fun ecmProtocolIndex(context: Context): Int = defaultPrefs(context).getInt(Constants.PREFS_ECM_PROTOCOL, 0)

    @JvmStatic
    fun setEcmProtocolIndex(context: Context, index: Int) {
        defaultPrefs(context).edit().putInt(Constants.PREFS_ECM_PROTOCOL, index).apply()
    }

    /** SAF tree URI where log files are written; `null` until the user picks a folder in `PrefsActivity`. */
    @JvmStatic
    fun storageLocation(context: Context): String? = defaultPrefs(context).getString("storage.location", null)

    @JvmStatic
    fun setStorageLocation(context: Context, uri: String) {
        defaultPrefs(context).edit().putString("storage.location", uri).apply()
    }

    /** Whether EEPROM burning is enabled, set via the `enable_burn_eeprom` `SwitchPreference`. */
    @JvmStatic
    fun isBurnEnabled(context: Context): Boolean = defaultPrefs(context).getBoolean(Constants.PREFS_ENABLE_BURN, false)

    /** Whether the setup screen hides variables absent from the connected ECM's definitions. */
    @JvmStatic
    fun hideNonexistentVars(context: Context): Boolean = defaultPrefs(context).getBoolean("hide_nonexistent_vars", false)

    /** Whether burning writes only touched EEPROM pages, set via the `enable_fast_burning` `SwitchPreference`. */
    @JvmStatic
    fun isFastBurnEnabled(context: Context): Boolean = defaultPrefs(context).getBoolean("enable_fast_burning", false)

    /** Whether the screen stays on while a log recording is in progress, set via the `keep_screen_on` `SwitchPreference`. */
    @JvmStatic
    fun keepScreenOnWhileRecording(context: Context): Boolean = defaultPrefs(context).getBoolean("keep_screen_on", false)

    // --- Per-activity preferences (Activity#getPreferences(MODE_PRIVATE)) ---
    //
    // Deliberately NOT folded into the app-wide store above: these are scoped
    // per hosting Activity instance today, and migrating scope would lose
    // users' existing per-screen values.

    /** Variable name assigned to data-channel slot [index] (0-based) on [activity]'s screen; [default] if unset. */
    @JvmStatic
    fun dataChannel(activity: Activity, index: Int, default: String?): String? =
        activity.getPreferences(Activity.MODE_PRIVATE).getString("channel" + (index + 1), default)

    /** Persists all data-channel selections in one write, matching the original single `Editor#apply()` call. */
    @JvmStatic
    fun saveDataChannels(activity: Activity, variableNames: Array<String?>) {
        val editor = activity.getPreferences(Activity.MODE_PRIVATE).edit()
        for (i in variableNames.indices) {
            editor.putString("channel" + (i + 1), variableNames[i] ?: "")
        }
        editor.apply()
    }

    /** Selected log-interval spinner index on [activity]'s Log screen. */
    @JvmStatic
    fun logIntervalIndex(activity: Activity): Int =
        activity.getPreferences(Activity.MODE_PRIVATE).getInt("delay", 0)

    /** Whether to convert the `.bin` log to `.msl` after stopping a recording, on [activity]'s Log screen. */
    @JvmStatic
    fun convertLogOnSave(activity: Activity): Boolean =
        activity.getPreferences(Activity.MODE_PRIVATE).getBoolean("convertlog", false)

    /** Persists the log interval/convert-on-save switch in one write, matching the original single `Editor#apply()` call. */
    @JvmStatic
    fun saveLogSettings(activity: Activity, intervalIndex: Int, convertOnSave: Boolean) {
        activity.getPreferences(Activity.MODE_PRIVATE).edit()
            .putInt("delay", intervalIndex)
            .putBoolean("convertlog", convertOnSave)
            .apply()
    }

    // --- LLM provider credentials (PreferenceManager.getDefaultSharedPreferences) ---
    //
    // Set via `LlmSettingsActivity`'s `PreferenceFragmentCompat`
    // (`res/xml/llm_prefs.xml`), which persists to this same store (KTD6).

    /** Anthropic API key, set via the `llm_anthropic_key` `EditTextPreference`; `null` until configured. */
    @JvmStatic
    fun anthropicApiKey(context: Context): String? = defaultPrefs(context).getString("llm_anthropic_key", null)

    /** OpenAI API key, set via the `llm_openai_key` `EditTextPreference`; `null` until configured. */
    @JvmStatic
    fun openAiApiKey(context: Context): String? = defaultPrefs(context).getString("llm_openai_key", null)

    /** Google (Gemini) API key, set via the `llm_google_key` `EditTextPreference`; `null` until configured. */
    @JvmStatic
    fun googleApiKey(context: Context): String? = defaultPrefs(context).getString("llm_google_key", null)

    /** DeepSeek API key, set via the `llm_deepseek_key` `EditTextPreference`; `null` until configured. */
    @JvmStatic
    fun deepSeekApiKey(context: Context): String? = defaultPrefs(context).getString("llm_deepseek_key", null)

    /** OpenRouter API key, set via the `llm_openrouter_key` `EditTextPreference`; `null` until configured. Also the Kimi/Moonshot path (KD4). */
    @JvmStatic
    fun openRouterApiKey(context: Context): String? = defaultPrefs(context).getString("llm_openrouter_key", null)

    /** Rider-reachable Ollama server base URL, set via the `llm_ollama_base_url` `EditTextPreference`; `null` until configured. No on-device inference runs (R11). */
    @JvmStatic
    fun ollamaBaseUrl(context: Context): String? = defaultPrefs(context).getString("llm_ollama_base_url", null)

    /**
     * Providers with a non-blank credential set, in [ProviderId] declaration order. Ollama counts
     * as configured on a non-blank base URL alone (R11: no API key, just a reachable server);
     * every other provider requires only its single API key to be non-blank. Backs the R14
     * setup-prompt gate and the new-conversation provider picker (U9). AWS Bedrock has no
     * accessor here: not offered as a provider (see [ProviderId]'s KDoc for why).
     */
    @JvmStatic
    fun configuredProviders(context: Context): List<ProviderId> {
        val configured = mutableListOf<ProviderId>()
        if (!anthropicApiKey(context).isNullOrBlank()) configured.add(ProviderId.ANTHROPIC)
        if (!openAiApiKey(context).isNullOrBlank()) configured.add(ProviderId.OPENAI)
        if (!googleApiKey(context).isNullOrBlank()) configured.add(ProviderId.GOOGLE)
        if (!deepSeekApiKey(context).isNullOrBlank()) configured.add(ProviderId.DEEPSEEK)
        if (!openRouterApiKey(context).isNullOrBlank()) configured.add(ProviderId.OPENROUTER)
        if (!ollamaBaseUrl(context).isNullOrBlank()) configured.add(ProviderId.OLLAMA)
        return configured
    }

    /**
     * Resolves [providerId]'s currently-saved [ProviderCredentials] (KTD6) -
     * the shape [biz.logicminds.buelltune.chat.ChatRepository.sendMessage]
     * needs on every call (credentials are never persisted alongside a
     * conversation, see that method's KDoc). Callers should re-read this
     * immediately before each send rather than caching it, so an
     * in-[LlmSettingsActivity] credential edit takes effect on the next
     * turn without an app restart.
     */
    @JvmStatic
    fun credentialsFor(context: Context, providerId: ProviderId): ProviderCredentials = when (providerId) {
        ProviderId.ANTHROPIC -> ProviderCredentials(apiKey = anthropicApiKey(context))
        ProviderId.OPENAI -> ProviderCredentials(apiKey = openAiApiKey(context))
        ProviderId.GOOGLE -> ProviderCredentials(apiKey = googleApiKey(context))
        ProviderId.DEEPSEEK -> ProviderCredentials(apiKey = deepSeekApiKey(context))
        ProviderId.OPENROUTER -> ProviderCredentials(apiKey = openRouterApiKey(context))
        ProviderId.OLLAMA -> ProviderCredentials(baseUrl = ollamaBaseUrl(context))
    }
}
