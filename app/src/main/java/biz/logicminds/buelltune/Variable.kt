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

import android.util.Log

import java.io.IOException
import java.text.DecimalFormat

/**
 * A Variable holds the name, location, length, format, type and other
 * properties of information either stored in the [EEPROM] or runtime
 * data.
 *
 * Note: the original Java class implemented `Cloneable`, but nothing in the
 * codebase ever called `.clone()` on a Variable (confirmed via a repo-wide
 * grep for `.clone()` and `Cloneable` -- both hits were only the
 * `implements Cloneable` declaration itself), so that interface is dropped
 * rather than ported (per KTD8/plan U4 approach step 8).
 *
 * @see VariableProvider
 */
class Variable {

	/**
	 * Variable data type (scalar, bit, array, etc.).
	 */
	enum class DataType {
		SCALAR, VALUE, BITS, BITFIELD, ARRAY, AXIS, TABLE, MAP, STRING
	}

	var id: Int = 0
	var ecmType: ECM.Type? = null
	var name: String? = null
	var type: DataType? = null
	var size: Int = 0

	var width: Int = 0
		set(value) {
			field = maxOf(value, 1)
		}

	var rows: Int = 0
	var cols: Int = 0
	var offset: Int = 0
	var unit: String? = ""
	var symbol: String? = ""
	var scale: Double = 0.0
	var translate: Double = 0.0
	var label: String? = null

	var format: String? = null
		set(value) {
			field = value
			if (value != null) {
				formatter = DecimalFormat(value)
			}
		}

	private var formatter: DecimalFormat = DEFAULT_FORMAT

	var low: Double = 0.0

	// Backing storage for high/getHigh()/setHigh() kept as a distinct
	// property name (highRaw) rather than a Kotlin custom-getter property
	// named "high": toString() below reads the *raw* stored value the way
	// the original Java toString() does (direct same-class field access
	// bypasses Java getters too), while getHigh() applies the translation.
	// A Kotlin property named "high" with a custom get() would make every
	// reference to "high" -- including from toString() -- go through that
	// custom getter, changing toString()'s output.
	private var highRaw: Double = 0.0

	fun getHigh(): Double {
		// If uhigh is not max, high must be translated.
		return if ((size == 1 && uhigh == 0xFF) || (size == 2 && uhigh == 0xFFFF)) {
			highRaw * scale + translate
		} else {
			highRaw
		}
	}

	fun setHigh(high: Double) {
		highRaw = high
	}

	var ulow: Int = 0
	var uhigh: Int = 0
	var remarks: String? = null

	private var rawValues: Array<Any?>? = null
	private var formattedValues: Array<String?>? = null

	var description: String? = null

	fun getElementCount(): Int = rawValues?.size ?: 0

	fun init() {
		formattedValues = arrayOfNulls(size / width)
		rawValues = arrayOfNulls(formattedValues!!.size)
	}

	fun getRawValue(): Any? = rawValues!![0]

	fun getRawValueAt(index: Int): Any? = rawValues!![index]

	fun setFormattedValue(formattedValue: String?) {
		formattedValues!![0] = formattedValue
	}

	override fun toString(): String {
		return "Variable[id: $id, name:$name" +
			", ECM: $ecmType, type: $type" +
			", size: $size, offset: $offset" +
			", unit: $unit, scale:$scale" +
			", trn: $translate, high: $highRaw" +
			"]"
	}

	fun refreshValue(tmp: ByteArray?): Variable {
		val co = if (offset < 0) tmp!!.size + offset else offset
		if (tmp != null && co >= 0 && co + size <= tmp.size) {
			for (s in 0 until (size / width)) {
				var value = 0
				for (i in width downTo 1) {
					value = value shl 8
					value = value or (tmp[co + s * width + i - 1].toInt() and 0xff)
				}
				if (type == DataType.BITS || type == DataType.BITFIELD) {
					rawValues!![s] = (value and 0xffff).toShort()
				} else if (type != DataType.STRING) {
					var v = value.toDouble()
					if (scale != 0.0) {
						v *= scale
					}
					if (translate != 0.0) {
						v += translate
					}
					rawValues!![s] = v
					if ("0" == format) {
						rawValues!![s] = (rawValues!![s] as Double).toInt()
					}
				} else if (type == DataType.STRING) {
					val bytes = ByteArray(size)
					System.arraycopy(tmp, co, bytes, 0, size)
					rawValues!![s] = bytes
				} else {
					Log.w(TAG, "Unsupported type $type")
				}
				formatValueAt(s)
			}
		}
		return this
	}

	fun getFormattedValue(): String? = formattedValues!![0]

	@Throws(ArrayIndexOutOfBoundsException::class)
	fun getFormattedValueAt(index: Int): String? = formattedValues!![index]

	fun getFormattedValueAt(row: Int, col: Int): Any? = formattedValues!![row * cols + col]

	fun getValueAsString(): String {
		if (type == DataType.BITS || type == DataType.BITFIELD) {
			return getFormattedValue()!!
		}
		return formatter.format(rawValues!![0])
	}

	fun getIntValue(): Int = getIntValueAt(0)

	fun getIntValueAt(row: Int, col: Int): Int = getIntValueAt(row * cols + col)

	fun getIntValueAt(index: Int): Int {
		if (type == DataType.BITFIELD || type == DataType.BITS) {
			return (rawValues!![index] as Short).toInt()
		}

		val v = rawValues!![index]
		if (v != null) {
			return if (v is Int) {
				v
			} else {
				(v as Double).toInt()
			}
		}
		return 0
	}

	@Throws(IOException::class)
	fun updateValue(bytes: ByteArray) {
		if (rawValues!![0] == null) {
			return
		}
		val co = if (offset < 0) bytes.size + offset else offset
		val buffer = ByteArray(size)
		for (s in 0 until (size / width)) {
			var value: Int
			if (type == DataType.BITFIELD || type == DataType.BITS) {
				value = (rawValues!![0] as Short).toInt() and 0xFFFF
			} else if (type != DataType.STRING) {
				var v: Double = if (rawValues!![s] is Double) {
					rawValues!![s] as Double
				} else {
					(rawValues!![s] as Int).toDouble()
				}
				if (translate != 0.0) {
					v -= translate
				}
				if (scale != 0.0) {
					v /= scale
				}
				value = v.toInt()
			} else {
				Log.w(TAG, "Unsupported type $type")
				return
			}

			for (i in 0 until width) {
				buffer[i + s * width] = (value and 0xFF).toByte()
				value = value shr 8
			}
		}
		Log.d(
			TAG,
			String.format(
				"Setting buffer (len: %X) at offset 0x%02X (raw: 0x%02X) to %s (width: %d).",
				bytes.size, co, offset, Utils.hexdump(buffer), size
			)
		)
		System.arraycopy(buffer, 0, bytes, co, size)
		Log.d(TAG, "Result: " + Utils.hexdump(bytes, co, co + size))
	}

	@Throws(NumberFormatException::class)
	fun parseValue(value: Any?) {
		parseValueAt(0, value)
	}

	@Throws(NumberFormatException::class)
	fun parseValueAt(index: Int, value: Any?) {
		if (value != null) {
			val v = value.toString().toDouble()
			rawValues!![index] = v

			if ("0" == format) {
				rawValues!![index] = (rawValues!![index] as Double).toInt()
			}
			formatValueAt(index)
		}
	}

	fun parseValueAt(row: Int, col: Int, value: Any?) {
		parseValueAt(row * cols + col, value)
	}

	private fun formatValueAt(index: Int) {
		if (type == DataType.BITS || type == DataType.BITFIELD) {
			val v = rawValues!![index] as Short
			formattedValues!![index] = Integer.toBinaryString(0x100 or v.toInt()).substring(1)
		} else if (type == DataType.STRING) {
			val raw = rawValues!![index] as ByteArray
			var len = raw.size
			for (i in raw.indices) {
				if (raw[i].toInt() == 0) {
					len = i
					break
				}
			}
			formattedValues!![index] = String(raw, 0, len)
		} else {
			formattedValues!![index] = formatter.format(rawValues!![index])
			if (!Utils.isEmptyString(symbol)) {
				formattedValues!![index] = formattedValues!![index] + symbol
			}
		}
	}

	companion object {
		private const val TAG = "Variable"
		private val DEFAULT_FORMAT = DecimalFormat("0")
	}
}
