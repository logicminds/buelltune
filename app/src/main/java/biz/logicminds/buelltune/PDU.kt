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

import java.text.ParseException

/**
 * This class represents a data frame exchanged with the ECM via
 * [ECM.sendPDU] and (as of U7) [biz.logicminds.buelltune.transport.PduFraming.readFrame].
 * A PDU consists of a header, payload and trailing checksum.
 */
class PDU {
	private val pdu: ByteArray

	/**
	 * Enumeration of ECM functions. Used for performing an active test or
	 * clearing stored error codes.
	 */
	enum class Function(code: Int, private val label: String) {
		ClearCodes(1, "Clear Codes"),
		FrontCoil(2, "Front Coil"),
		RearCoil(3, "Rear Coil"),
		Tachometer(4, "Tachometer"),
		FuelPump(5, "Fuel Pump"),
		FrontInj(6, "Front Injector"),
		Rear_Inj(7, "Rear Injector"),
		TPS_Reset(8, "TPS Reset"),
		Fan(9, "Fan"),
		Exh_Valve(0x0a, "Exhaust Valve"),
		Active_Intake(0x0b, "Active Intake"),
		Shift_Light(0x0c, "Shift Light");

		val code: Byte = (code and 0xff).toByte()

		override fun toString(): String {
			return label
		}
	}

	/**
	 * Parse a PDU from a raw byte buffer.
	 */
	@Throws(ParseException::class)
	constructor(packet: ByteArray, len: Int) {
		pdu = ByteArray(len)
		System.arraycopy(packet, 0, pdu, 0, len)
		validate()
	}

	constructor(sender: Byte, recipient: Byte, payload: ByteArray) {
		var i = 0
		pdu = ByteArray(payload.size + 8)
		pdu[i++] = SOH
		pdu[i++] = sender
		pdu[i++] = recipient
		pdu[i++] = (payload.size + 1).toByte()
		pdu[i++] = EOH
		pdu[i++] = SOT
		System.arraycopy(payload, 0, pdu, i, payload.size)
		i += payload.size
		pdu[i++] = EOT
		pdu[i++] = checksum()
	}

	@Throws(ParseException::class)
	private fun validate() {
		if (pdu.size < 9) {
			throw ParseException("Short packet length.", 0)
		}
		// Check for markers
		if (pdu[0] != SOH) {
			throw ParseException("Packet does not start with SOH.", 0)
		}
		if (pdu[4] != EOH) {
			throw ParseException("No EOH detected.", 4)
		}
		if (pdu[5] != SOT) {
			throw ParseException("No SOT detected.", 5)
		}
		val size = pdu[3].toInt() and 0xff
		if (pdu.size - 7 != size) {
			throw ParseException("Size/Length mismatch (" + (pdu.size - 7) + "/" + size + ")", 3)
		}
		if (pdu[pdu.size - 2] != EOT) {
			throw ParseException("No EOT detected.", 2)
		}
		// Checksum
		val cs = checksum()
		if (cs != pdu[pdu.size - 1]) {
			throw ParseException(
				"Invalid checksum (" + Integer.toHexString(cs.toInt()) + "/" +
					Integer.toHexString(pdu[pdu.size - 1].toInt() and 0xff) + ")",
				pdu.size - 1
			)
		}
	}

	fun getSender(): Byte {
		return pdu[1]
	}

	fun getRecipient(): Byte {
		return pdu[2]
	}

	fun getDataLength(): Int {
		return pdu[3].toInt() and 0xff
	}

	fun getPayload(): ByteArray {
		val ret = ByteArray(getDataLength() - 1)
		System.arraycopy(pdu, 6, ret, 0, ret.size)
		return ret
	}

	fun getEEPromData(): ByteArray {
		val ret = ByteArray(getDataLength() - (if (isRequest()) 4 else 2))
		System.arraycopy(pdu, if (isRequest()) 9 else 7, ret, 0, ret.size)
		return ret
	}

	@Throws(IllegalStateException::class)
	fun getPageNr(): Int {
		var ret = -1
		if (isRequest() && (pdu[6] == CMD_GET.toByte() || pdu[6] == CMD_SET.toByte())) {
			ret = pdu[8].toInt() and 0xff
		}
		return ret
	}

	@Throws(IllegalStateException::class)
	fun getPageOffset(): Int {
		var ret = -1
		if (isRequest() && (pdu[6] == CMD_GET.toByte() || pdu[6] == CMD_SET.toByte())) {
			ret = pdu[7].toInt() and 0xff
		}
		return ret
	}

	fun getCommand(): Int {
		return if (isRequest()) getErrorIndicator() else 0
	}

	fun getErrorIndicator(): Int {
		// NB: the (byte) narrow immediately widened back to Int on return is a
		// verbatim transcription of the original Java's round trip through
		// "(byte) (pdu[6] & 0xff)" auto-widened to the int return type -- the
		// & 0xff has no observable effect since the subsequent implicit widen
		// re-sign-extends, so this returns pdu[6] sign-extended, not unsigned.
		return if (isResponse()) ((pdu[6].toInt() and 0xff).toByte()).toInt() else 0
	}

	fun isACK(): Boolean {
		return isResponse() && pdu[6] == ACK
	}

	fun isRequest(): Boolean {
		return getRecipient() == ECM_ID
	}

	fun isResponse(): Boolean {
		return getSender() == ECM_ID
	}

	fun getBytes(): ByteArray {
		return pdu
	}

	override fun toString(): String {
		return Utils.hexdump(pdu, 0, pdu.size)
	}

	private fun checksum(): Byte {
		var cs: Byte = 0
		for (i in 1 until pdu.size - 1) {
			cs = (cs.toInt() xor pdu[i].toInt()).toByte()
		}
		return cs
	}

	companion object {
		const val CMD_RTDATA: Byte = 0x43
		const val CMD_SET: Int = 0x57
		const val CMD_GET: Int = 0x52
		const val CMD_VERSION: Byte = 0x56
		const val ACK: Byte = 0x06
		const val DROID_ID: Byte = 0x00
		const val STOCK_ECM_ID: Byte = 0x42
		const val RACE_ECM_ID: Byte = 0x55

		const val SOH: Byte = 0x01

		// 0xFF (255) does not fit the signed Byte range as a literal, so this
		// cannot be `const`; matches Java's explicit `(byte) 0xFF` cast.
		@JvmField
		val EOH: Byte = 0xFF.toByte()
		const val SOT: Byte = 0x02
		const val EOT: Byte = 0x03

		private var ECM_ID: Byte = STOCK_ECM_ID

		private var GET_VERSION: PDU = PDU(DROID_ID, ECM_ID, byteArrayOf(0x56))
		private var GET_RT: PDU = PDU(DROID_ID, ECM_ID, byteArrayOf(0x43))
		private var GET_CSTATE: PDU = getRequest(0x20, 0, 1)

		/**
		 * Set the ECM protocol to use.
		 *
		 * @param protocol the Protocol (STOCK or FACTORY_RACE)
		 */
		@JvmStatic
		fun setProtocol(protocol: ECM.Protocol?) {
			val id = if (ECM.Protocol.FACTORY_RACE == protocol) RACE_ECM_ID else STOCK_ECM_ID
			if (id != ECM_ID) {
				ECM_ID = id
				GET_VERSION = PDU(DROID_ID, ECM_ID, byteArrayOf(0x56))
				GET_RT = PDU(DROID_ID, ECM_ID, byteArrayOf(0x43))
				GET_CSTATE = getRequest(0x20, 0, 1)
			}
		}

		/**
		 * Get the currently used ECM address (ID)
		 *
		 * @return [STOCK_ECM_ID] or [RACE_ECM_ID]
		 */
		@JvmStatic
		fun getECMID(): Byte {
			return ECM_ID
		}

		/**
		 * Construct a EEPROM GET Request
		 *
		 * @param pageno EEPROM page number to read from
		 * @param offset offset within selected page
		 * @param len    number of bytes to read
		 */
		@JvmStatic
		fun getRequest(pageno: Int, offset: Int, len: Int): PDU {
			val payload = ByteArray(4)
			payload[0] = CMD_GET.toByte()
			payload[1] = (offset and 0xff).toByte()
			payload[2] = (pageno and 0xff).toByte()
			payload[3] = (len and 0xff).toByte()
			return PDU(DROID_ID, ECM_ID, payload)
		}

		/**
		 * Construct a EEPROM SET Request
		 *
		 * @param pageno EEPROM page number to read from
		 * @param offset offset within selected page
		 * @param data   the data to write to the EEPROM at the specified position
		 * @param pos    the offset within the data buffer
		 * @param len    the number of bytes to include in the SET request
		 */
		@JvmStatic
		fun setRequest(pageno: Int, offset: Int, data: ByteArray, pos: Int, len: Int): PDU {
			val payload = ByteArray(3 + len)
			payload[0] = CMD_SET.toByte()
			payload[1] = (offset and 0xff).toByte()
			payload[2] = (pageno and 0xff).toByte()
			System.arraycopy(data, pos, payload, 3, len)
			return PDU(DROID_ID, ECM_ID, payload)
		}

		/**
		 * Construct a Function Trigger
		 *
		 * @param function the function to trigger
		 */
		@JvmStatic
		fun commandRequest(function: Function): PDU {
			return PDU(DROID_ID, ECM_ID, byteArrayOf(CMD_SET.toByte(), 0, 0x20, function.code))
		}

		@JvmStatic
		fun getVersion(): PDU {
			return GET_VERSION
		}

		@JvmStatic
		fun getRuntimeData(): PDU {
			return GET_RT
		}

		@JvmStatic
		fun getCurrentState(): PDU {
			return GET_CSTATE
		}
	}
}
