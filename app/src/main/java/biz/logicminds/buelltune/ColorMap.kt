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

/**
 * Map an octet value to a corresponding ARGB color code.
 */
object ColorMap {

	private val colors = IntArray(256)

	init {
		var r = 0
		var g = 0
		var b = 256
		for (i in 0 until 256) {
			colors[i] = argb(255, minOf(255, r), minOf(255, g), minOf(255, b))
			if (i < 64) {
				g += 4
			} else if (i < 128) {
				b -= 4
			} else if (i < 192) {
				r += 4
			} else {
				g -= 4
			}
		}
	}

	// Matches the documented packing order of android.graphics.Color.argb(a, r, g, b):
	// (a << 24) | (r << 16) | (g << 8) | b -- inlined so this class has zero Android dependency.
	private fun argb(a: Int, r: Int, g: Int, b: Int): Int {
		return (a shl 24) or (r shl 16) or (g shl 8) or b
	}

	@JvmStatic
	fun getColor(value: Byte): Int {
		return colors[value.toInt() and 0xff]
	}

}
