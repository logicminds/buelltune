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
 * `Units` maps a unit name to its symbol (e.g. "Degree" maps to "°").
 */
object Units {
	private val map: Map<String, String> = mapOf(
		"Degree" to "°",
		"Degree BTDC" to "°",
		"Degree C" to "°",
		"Degrees" to "°",
		"Degrees BDD" to "°",
		"Degrees C" to "°",
		"Percent" to "%",
		"TE degC" to "°",
		"Volt" to "V",
		"Volts" to "V"
	)

	@JvmStatic
	fun getSymbol(unit: String?): String? {
		if (unit != null) {
			return map[unit]
		}
		return null
	}
}
