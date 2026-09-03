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
 * A write/reset/flash suggestion the model attached to its answer (KD3,
 * KTD8): tapping it deep-links to the exact existing drawer screen named by
 * [screenId] where the rider performs the action manually - the app never
 * pre-fills a value or triggers it itself (R2). Whether [screenId] actually
 * matches one of `res/menu/main_drawer.xml`'s ids is a U9 (UI-layer)
 * concern, not this file's.
 */
data class SuggestionCard(val screenId: String, val label: String)

/**
 * `[[SUGGEST:<drawer-item-id>|<short action label>]]`, anchored to the end
 * of the model's final text (KTD8). Only that exact bracket/pipe shape is
 * recognized; anything else - including a marker missing its label, or one
 * that isn't at the very end of the text - degrades to plain text with no
 * card rather than throwing.
 */
private val SUGGEST_MARKER = Regex("""\[\[SUGGEST:([^|\]]+)\|([^\]]+)]]\s*$""")

/**
 * Strips a trailing `[[SUGGEST:...]]` marker (KTD8) from [rawText], returning
 * the display text with the marker removed and the parsed [SuggestionCard],
 * or the original text unchanged and `null` when no well-formed marker is
 * present.
 */
fun extractSuggestion(rawText: String): Pair<String, SuggestionCard?> {
    val match = SUGGEST_MARKER.find(rawText) ?: return rawText to null
    val screenId = match.groupValues[1].trim()
    val label = match.groupValues[2].trim()
    if (screenId.isEmpty() || label.isEmpty()) {
        return rawText to null
    }
    val displayText = rawText.substring(0, match.range.first).trimEnd()
    return displayText to SuggestionCard(screenId, label)
}
