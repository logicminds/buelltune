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

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import biz.logicminds.buelltune.R

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
 * Reached via `Intent` from Chat's setup prompt (R14) and its
 * new-conversation provider picker (R10/U9); it has no `main_drawer.xml`
 * entry of its own.
 */
class LlmSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, LlmPreferenceFragment())
                .commit()
        }
    }

    /** Hosts `res/xml/llm_prefs.xml` -- one `EditTextPreference` per provider credential field. */
    class LlmPreferenceFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.llm_prefs, rootKey)
        }
    }
}
