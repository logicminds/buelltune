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

import biz.logicminds.buelltune.ECM.Type

/**
 * A single bit of information. It usually is part of a [BitSet].
 */
class Bit {
	var id: Int = 0
	var type: Type? = null
	var offset: Int = 0
	var byteNr: Int = 0
	var bitNr: Int = 0
	var code: String? = null
	var name: String? = null
	var remark: String? = null

	// Backing field for the value accessors below is deliberately not named
	// "value": a plain `var value: Byte` would make Kotlin auto-generate
	// getValue()/setValue(Byte), colliding with the hand-written
	// getValue()/setValue(Boolean) pair below (asymmetric types, so this
	// cannot be a single Kotlin property).
	private var byteValue: Byte = 0

	fun getValue(): Byte = byteValue

	fun setValue(value: Boolean) {
		byteValue = if (!value) 0.toByte() else ((1 shl bitNr) and 0xff).toByte()
	}

	fun refreshValue(data: ByteArray): Boolean {
		var o = offset
		if (o >= data.size) {
			byteValue = 0
			return false
		} else if (o < 0) {
			o = data.size + o
		}
		val mask = (1 shl bitNr).toByte()
		byteValue = ((data[o].toInt() and 0xff) and mask.toInt()).toByte()
		return byteValue.toInt() != 0
	}

	fun isSet(): Boolean {
		return byteValue.toInt() != 0
	}

	override fun toString(): String {
		return "Bit[name: $name, remark: $remark, ECM: $type, offset: $offset, byte: $byteNr, bit: $bitNr, code: $code]"
	}
}
