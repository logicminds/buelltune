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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The outcome of a single [EcmTools] read (R6, R7). Every tool method
 * returns exactly one of these four cases rather than throwing, so a Koog
 * tool adapter (U5) always has a structured, serializable result to hand
 * back to the model regardless of ECM/EEPROM state.
 *
 * [Ok] carries a compact, human-readable JSON payload with units already
 * applied - never raw bytes (R6). [NotConnected] and [EepromNotRead] are
 * deliberately distinct cases (not folded together): "no ECM connected"
 * and "connected, but the rider has never fetched EEPROM data" are
 * different rider-facing situations that call for different guidance, and
 * a chat agent consuming this type must be able to tell them apart without
 * string-matching an error message. [Error] covers everything else -
 * including a single unresolvable name/range - without aborting the whole
 * tool call via an exception (R5, R7).
 */
@Serializable
sealed class ToolResult {

    @Serializable
    @SerialName("ok")
    data class Ok(val payload: JsonObject) : ToolResult()

    @Serializable
    @SerialName("not_connected")
    data object NotConnected : ToolResult()

    @Serializable
    @SerialName("eeprom_not_read")
    data object EepromNotRead : ToolResult()

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : ToolResult()
}
