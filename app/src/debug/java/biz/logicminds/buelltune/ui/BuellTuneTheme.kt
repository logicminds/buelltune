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
package biz.logicminds.buelltune.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import biz.logicminds.buelltune.R

/**
 * Debug-only light [androidx.compose.material3.ColorScheme] sourcing the
 * BuellTune brand palette from `colors.xml` (`colorPrimary`, `colorAccent`)
 * so `colors.xml` stays the single source of truth instead of duplicating
 * hex literals here. `background`/`surface`/`onBackground`/`onSurface` are
 * pinned to a neutral off-white/near-black pair (matching the legacy
 * AppCompat light theme's chrome) so the shell reads as BuellTune-branded
 * rather than Compose Material3's stock lavender-tinted baseline scheme.
 * Everything else falls back to [lightColorScheme]'s own defaults; no dark
 * variant is defined, matching the legacy app's light-only theming.
 */
@Composable
fun BuellTuneTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = colorResource(R.color.colorPrimary),
        onPrimary = Color.White,
        secondary = colorResource(R.color.colorAccent),
        onSecondary = Color.White,
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF1A1A1A),
        surface = Color(0xFFFAFAFA),
        onSurface = Color(0xFF1A1A1A),
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}
