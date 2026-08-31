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
 * An ECM error consisting of a code, description and type.
 */
class Error {
	/**
	 * Type of error (current, recent, stored).
	 */
	enum class ErrorType {
		CURRENT, RECENT, STORED
	}

	var code: String? = null
	var description: String? = null
	var type: ErrorType? = null

	override fun toString(): String {
		return "Error[type: $type, code: $code, description: $description]"
	}
}
