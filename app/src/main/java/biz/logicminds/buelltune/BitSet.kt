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

import java.util.LinkedList

/**
 * A `BitSet` holds at most 8 [Bit]'s (and therefore could by termed 'Byte').
 */
class BitSet(val name: String?, val label: String?, var offset: Int) : Iterable<Bit> {
	private val bits = arrayOfNulls<Bit>(8)

	fun getActiveBits(data: ByteArray): BitSet {
		val active = BitSet(name, label, offset)
		for (b in bits) {
			if (b != null && b.refreshValue(data)) {
				active.add(b)
			}
		}
		return active
	}

	fun add(bit: Bit) {
		bits[bit.bitNr] = bit
	}

	fun getBit(bit: Int): Bit? = bits[bit]

	/**
	 * Set / Clear all bits in this set
	 */
	fun setAll(value: Boolean) {
		for (bit in bits) {
			bit?.setValue(value)
		}
	}

	/**
	 * Return a byte with appropriate bits in this set enabled
	 */
	fun getValue(): Byte {
		var result = 0
		for (bit in bits) {
			if (bit != null) {
				result = result or bit.getValue().toInt()
			}
		}
		return result.toByte()
	}

	/**
	 * Return the bitmask for all bits in this set.
	 */
	fun getMask(): Byte {
		var result = 0
		for (bit in bits) {
			if (bit != null) {
				result = result or ((1 shl bit.bitNr) and 0xFF)
			}
		}
		return result.toByte()
	}

	/**
	 * Update bits in given byte array according to the bits in this set.
	 *
	 * @return true if the underlying byte actually changed
	 */
	fun updateValue(bytes: ByteArray): Boolean {
		val nval = getValue()
		val mask = getMask()
		val co = if (offset < 0) bytes.size + offset else offset
		val oldval = bytes[co]
		var value = (oldval.toInt() and mask.toInt().inv()) and 0xFF
		value = value or nval.toInt()
		val newval = value.toByte()
		// Log.d(TAG, String.format("Setting bit set '%s', offset 0x%04X, to %s", name, co, (Integer.toBinaryString(0x100 | (val & 0xFF)).substring(1,9))));
		bytes[co] = newval
		return newval != oldval
	}

	override fun iterator(): Iterator<Bit> {
		val result = LinkedList<Bit>()
		for (b in bits) {
			if (b != null) {
				result.add(b)
			}
		}
		return result.iterator()
	}
}
