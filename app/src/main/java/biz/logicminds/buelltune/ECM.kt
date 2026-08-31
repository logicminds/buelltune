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

import android.content.Context
import android.text.format.DateFormat
import android.util.Log

import biz.logicminds.buelltune.Constants.DataSource
import biz.logicminds.buelltune.Constants.Variables
import biz.logicminds.buelltune.EEPROM.Page
import biz.logicminds.buelltune.Error.ErrorType
import biz.logicminds.buelltune.PDU.Function
import biz.logicminds.buelltune.transport.ConnectionState
import biz.logicminds.buelltune.transport.EcmTransport

import java.io.IOException
import java.util.Calendar
import java.util.LinkedList

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * This class represents the main interface to your Buell ECM. Communication
 * with the ECM may take place via a Bluetooth SPP adapter, BLE, USB-serial,
 * or TCP/IP. Functions include:
 *  * Reading stored errors
 *  * Executing "Active" Tests (e.g. run the fuel pump)
 *  * reading runtime ("live") data
 *  * reading and writing EEPROM data (e.g. ECM settings)
 *
 * When using a bluetooth adapter, the Initial pairing of your android device
 * and the adapter must be done using the Android Settings application (Wireless
 * & Network). Also, make sure that the adapter is set to 9600, 8N1, No
 * Handshake.
 *
 * Use [ECM.getInstance] as a starting point, build an [EcmTransport] via
 * `TransportFactory` for the desired connection type, and call
 * [ECM.connect] with it to establish a connection to the ECM. Before EEPROM
 * data can be accessed, you must call [ECM.setupEEPROM].
 *
 * Dependencies are constructor-injected (KTD5, KTD7): [variableProvider] and
 * [bitsetProvider] resolve runtime/EEPROM variable definitions,
 * [definitionsProvider] resolves the EEPROM page-layout skeleton for a given
 * ECM id, and [context] is only needed for [getMfgDate]'s locale-aware date
 * formatting - it is nullable so this class stays constructible from a plain
 * JVM test (R8's IOException scenario, R5's chunking scenarios) without a
 * real Android [Context].
 */
class ECM(
    private val variableProvider: VariableProvider,
    private val bitsetProvider: BitSetProvider,
    private val definitionsProvider: EcmDefinitionsProvider,
    private val context: Context?,
) {

    /**
     * ECM Type. DDFI-1 (Tubers), DDFI-2 (XBs -2007), DDFI-3 (XB 2008-, 1125R/CR)
     */
    enum class Type {
        DDFI1, DDFI2, DDFI3;

        companion object {
            @JvmStatic
            fun getType(type: String?): Type? = when (type) {
                "DDFI" -> DDFI1
                "DDFI-2" -> DDFI2
                "DDFI-3" -> DDFI3
                else -> null
            }
        }
    }

    /**
     * Supported ECM protocols (stock or factory-race).
     */
    enum class Protocol(private val label: String) {
        STOCK("Stock / P&A"), FACTORY_RACE("Factory Race");

        override fun toString(): String = label
    }

    private var transport: EcmTransport? = null
    private var eeprom: EEPROM? = null
    private var recording = false
    private var protocol = Protocol.STOCK

    /** getRuntimeData()/setRuntimeData(byte[]) -- Kotlin's auto-generated accessors for this name already match. */
    var runtimeData: ByteArray? = null

    /**
     * Connect to the ECM using the given, already target-bound [transport]
     * (built by `TransportFactory` for the user's chosen connection type -
     * Bluetooth Classic, BLE, USB-serial, or TCP/IP - per R7). Replaces the
     * four legacy `connect()` overloads that used to build a raw socket
     * inline.
     *
     * Blocks the calling thread until the handshake completes or fails,
     * bridging into [EcmTransport]'s suspend API via [runBlocking] on
     * [Dispatchers.IO] (KTD11): `MainActivity.ConnectTask`, `FetchTask`,
     * `BurnTask`, and the poll loop are Java `AsyncTask`s that cannot invoke
     * a Kotlin suspend function directly, so `ECM` keeps its blocking
     * signature rather than becoming `suspend` itself.
     *
     * On success, records [protocol] and calls [PDU.setProtocol] - matching
     * where each of the four legacy overloads used to do so, and only after
     * a successful connect, so a failed attempt never silently changes
     * which ECM id subsequent PDUs address.
     */
    @Throws(Exception::class)
    fun connect(transport: EcmTransport, protocol: Protocol) {
        this.transport = transport
        runBlocking(Dispatchers.IO) { transport.connect() }
        this.protocol = protocol
        PDU.setProtocol(protocol)
    }

    /**
     * Disconnect from the ECM. Safe to call even if not currently
     * connected.
     */
    @Synchronized
    @Throws(IOException::class)
    fun disconnect() {
        val active = transport ?: return
        try {
            runBlocking(Dispatchers.IO) { active.disconnect() }
        } finally {
            runtimeData = null
        }
    }

    /**
     * Send a protocol data unit (PDU) to the ECM and return the ECMs response
     */
    @Synchronized
    @Throws(IOException::class)
    private fun sendPDU(pdu: PDU): PDU {
        try {
            if (D) Log.d(TAG, "Sending: $pdu")
            val active = transport ?: throw IOException("Not connected to ECM.")
            val ret = runBlocking(Dispatchers.IO) { active.transact(pdu) }
            if (!ret.isResponse()) {
                throw IOException("No valid response from ECM (wrong Protocol?)")
            }
            if (!ret.isACK()) {
                throw IOException("Request not acknowledged by ECM (error code ${ret.getErrorIndicator()}).")
            }
            if (D) Log.d(TAG, "Received: $ret")
            return ret
        } catch (ioe: IOException) {
            Log.e(TAG, "IO Exception sending PDU", ioe)
            // The transport has already released its resources and moved
            // itself to Failed (KTD11) if this was a real link failure; ECM
            // only needs to drop its own now-stale runtime-data cache, the
            // f3337a1 behavior this mirrors at the protocol layer.
            runtimeData = null
            throw ioe
        } catch (rte: RuntimeException) {
            Log.e(TAG, "Runtime Exception sending PDU", rte)
            throw rte
        }
    }

    /**
     * Read out and return the version of the currently connected ECM. You must invoke
     * this method before EEPROM data can be accessed.
     *
     * @return the full ECM version string (e.g. "BUEIB310 12-11-03")
     */
    @Throws(IOException::class)
    fun setupEEPROM(): String {
        val ret = readVersion()
        Log.i(TAG, "ECM Version: $ret")
        // KTD7 seam: resolved through the injected definitions provider, not
        // a Context-taking static lookup, so a non-Android implementation
        // (U10's JVM/ecmsim harness) can be substituted here.
        val e = definitionsProvider.getEeprom(ret)
        eeprom = e
        if (e != null) {
            e.version = ret
        } else {
            Log.w(TAG, "Unknown ECM ID $ret")
            throw IOException("Unsupported ECM Version '$ret'!")
        }
        return ret
    }

    /**
     * Returns the currently active protocol (STOCK or FACTORY_RACE)
     */
    fun getCurrentProtocol(): Protocol = protocol

    /**
     * Return the current ECM version string
     */
    fun getVersion(): String? = eeprom?.version

    /**
     * Request the version string from the ECM
     */
    @Throws(IOException::class)
    fun readVersion(): String {
        val response = sendPDU(PDU.getVersion())
        return String(response.getEEPromData(), Charsets.US_ASCII)
    }

    /**
     * Returns the current state of the ECM (Busy/Idle)
     *
     * @return 0 if the ECM is idle, any other value if it's busy.
     */
    @Throws(IOException::class)
    fun getCurrentState(): Byte {
        val response = sendPDU(PDU.getCurrentState())
        return response.getEEPromData()[0]
    }

    /**
     * Check if the ECM is busy.
     *
     * @return true if it is busy, otherwise false.
     */
    @Throws(IOException::class)
    fun isBusy(): Boolean = getCurrentState().toInt() != 0

    /**
     * Run a test function
     *
     * @param function the function to invoke
     */
    @Throws(IOException::class)
    fun runTest(function: Function) {
        val response = sendPDU(PDU.commandRequest(function))
        if (!response.isACK()) {
            throw IOException("Test failed.")
        }
    }

    /**
     * Read a single page from the EEPROM. The data read will be stored within the byte array
     * of the EEPROM object holding the page.
     *
     * @param page the Page to read
     */
    @Throws(IOException::class)
    fun readEEPromPage(page: Page) {
        val buffer = page.getParent().getBytes()!!
        var i = 0
        while (i < page.length()) {
            var dtr = minOf(page.length() - i, 16)
            var offset = i
            if (page.nr() == 0) { // Page zero is special
                offset = PAGE_ZERO_OFFSET - page.length() + i + 1
                dtr = 1
            }
            if (D) {
                Log.d(
                    TAG,
                    "Reading $dtr bytes from page ${page.nr()} at offset $offset to local buffer at offset ${page.start() + i}",
                )
            }
            val response = sendPDU(PDU.getRequest(page.nr(), offset, dtr))
            if (response.getEEPromData().size != dtr) {
                throw IOException("Requested $dtr bytes from ECM but received ${response.getEEPromData().size}")
            }
            System.arraycopy(response.getEEPromData(), 0, buffer, page.start() + i, dtr)
            i += dtr
        }
    }

    /**
     * Write a single page to the EEPROM
     *
     * @param page the page to write
     */
    @Throws(IOException::class)
    fun writeEEPromPage(page: Page) {
        if (page.nr() == 0) { // Selectively write page zero
            val buffer = page.getBytes(0, page.length(), ByteArray(page.length()), 0)
            for (varName in PAGE_ZERO_VARS_TO_WRITE) {
                val v = getEEPROMValue(varName)
                if (v != null) {
                    Log.d(TAG, "Writing page 0 $varName")
                    sendPDU(PDU.setRequest(page.nr(), PAGE_ZERO_OFFSET + v.offset + 1, buffer, buffer.size + v.offset, v.size))
                }
            }
        } else {
            val buffer = page.getParent().getBytes()!!
            var i = 0
            while (i < page.length()) {
                val dtr = minOf(page.length() - i, 16)
                val offset = i
                sendPDU(PDU.setRequest(page.nr(), offset, buffer, page.start() + offset, dtr))
                i += dtr
            }
        }
        page.saved()
    }

    /**
     * Request runtime data from the ECM
     *
     * @return a byte[] holding the Runtime Data (payload only)
     */
    @Throws(IOException::class)
    fun readRTData(): ByteArray {
        val response = sendPDU(PDU.getRuntimeData())
        val data = response.getBytes()
        runtimeData = data
        return data
    }

    /**
     * Indicates if a connection to the underlying ECM is established.
     */
    fun isConnected(): Boolean = transport?.state?.value is ConnectionState.Connected

    /**
     * Returns a reference to the ECMs EEPROM contents.
     *
     * @return the EEPROM or null if you have not yet called
     * [setupEEPROM] after establishing a connection.
     */
    fun getEEPROM(): EEPROM? = eeprom

    /**
     * Retrieve all current or historic (stored) error codes.
     *
     * @param type the type of error
     * @return a (possibly empty) list of errors or null
     */
    @Throws(IOException::class)
    fun getErrors(type: ErrorType): Collection<Error>? {
        var field = if (type == ErrorType.CURRENT) "CDiag%d" else "HDiag%d_LD"
        var ds = DataSource.RUNTIME_DATA
        if (runtimeData == null && isConnected()) {
            readRTData()
        }

        var data = runtimeData
        if (data == null) {
            if (type == ErrorType.STORED && eeprom != null && eeprom!!.isEepromRead()) {
                if (D) Log.d(TAG, "No live data, falling back to EEPROM data for stored errors...")
                data = eeprom!!.getBytes()
                field = "HDiag%d"
                ds = DataSource.EEPROM
            } else {
                return null
            }
        }

        val errors: MutableList<Error> = LinkedList()
        if (data != null) {
            var i = 0
            while (true) {
                val f = String.format(field, i)
                val bitset = bitsetProvider.getBitSet(eeprom!!.id, f, ds) ?: break
                if (ds == DataSource.EEPROM && bitset.offset < 0 && !eeprom!!.hasPageZero()) {
                    if (D) Log.d(TAG, "$f: Skipping troublecode variable at offset ${bitset.offset}")
                    i++
                    continue
                }
                if (D) Log.d(TAG, "Checking field $f (offset ${bitset.offset}) for errors")
                for (bit in bitset) {
                    if (bit.refreshValue(data)) {
                        val e = Error()
                        e.code = bit.code
                        e.type = type
                        e.description = bit.remark
                        if (D) Log.d(TAG, "Error read: $e")
                        errors.add(e)
                    }
                }
                i++
            }
        }
        return errors
    }

    fun getRuntimeValue(name: String): Variable? {
        if (this.eeprom == null) return null
        val v = variableProvider.getRtVariable(getId()!!, name)
        if (v != null) {
            val tmp = runtimeData
            if (tmp != null) {
                v.refreshValue(tmp)
            }
        }
        return v
    }

    fun getFormattedEEPROMValue(name: String, defaultValue: String?): String? {
        val v = getEEPROMValue(name) ?: return defaultValue
        var ret = v.getFormattedValue()
        if (Utils.isEmptyString(ret)) {
            ret = defaultValue
        }
        return ret
    }

    fun getEEPROMValue(name: String?): Variable? {
        if (name == null || eeprom == null) {
            return null
        }

        val v = this.variableProvider.getEEPROMVariable(getId()!!, name)
        if (v != null) {
            if (v.offset < 0 && !eeprom!!.hasPageZero()) {
                return null
            }
            v.refreshValue(eeprom!!.getBytes()!!)
        }
        return v
    }

    /**
     * Get EEPROM Variable definition at given offset or just in front of it.
     *
     * @param offset the offset
     * @return a Variable or null if not found
     */
    fun getEEPROMValueNearOffset(offset: Int): Variable? = variableProvider.getNearestEEPROMVariable(getId()!!, offset)

    fun getEEPROMBit(name: String, bit: Int): Bit? {
        val bitset = bitsetProvider.getBitSet(getId()!!, name, DataSource.EEPROM)
        if (bitset != null) {
            val b = bitset.getBit(bit)!!
            if (b.offset < 0 && !eeprom!!.hasPageZero()) {
                return null
            }
            b.refreshValue(eeprom!!.getBytes()!!)
            return b
        }
        return null
    }

    fun getType(): Type? = eeprom?.type

    fun setEEPROM(eeprom: EEPROM?) {
        this.eeprom = eeprom
    }

    fun getId(): String? = eeprom?.id

    fun setRecording(recording: Boolean) {
        this.recording = recording
    }

    fun isRecording(): Boolean = recording

    fun getSerialNo(): String? = getFormattedEEPROMValue(Variables.KMFG_Serial, UNKNOWN)

    fun getMfgDate(): String {
        val year = getFormattedEEPROMValue(Variables.KMFG_Year, null)
        val day = getFormattedEEPROMValue(Variables.KMFG_Day, null)
        if (year != null && day != null) {
            var y = Integer.parseInt(year) + 2000
            if (y >= 2090) {
                y -= 100 // 1990..99
            }
            val d = Integer.parseInt(day) + 1
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, y)
            cal.set(Calendar.DAY_OF_YEAR, d)
            val df: java.text.DateFormat = DateFormat.getDateFormat(context)
            return df.format(cal.time)
        }
        return UNKNOWN
    }

    fun getCountryId(): String? {
        var id = getFormattedEEPROMValue(Variables.Country_ID, UNKNOWN)
        if ("255" == id) {
            id = String.format(
                "S%s-M%s-V%s",
                getFormattedEEPROMValue(Variables.KID_Series, "?"),
                getFormattedEEPROMValue(Variables.KID_Market, "?"),
                getFormattedEEPROMValue(Variables.KID_Version, "?"),
            )
        }
        return id
    }

    fun getCalibrationId(): String? = getFormattedEEPROMValue(Variables.Cal_ID, UNKNOWN)

    fun getLayoutRevision(): String? = getFormattedEEPROMValue(Variables.CSR, UNKNOWN)

    fun isEepromRead(): Boolean = eeprom != null && eeprom!!.isEepromRead()

    fun setEEPROMValue(v: Variable): Boolean {
        try {
            val bytes = eeprom!!.getBytes()!!
            v.updateValue(bytes)
            eeprom!!.touch(v.offset, bytes.size)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to update value. " + e.localizedMessage)
            return false
        }
        return true
    }

    fun setEEPROMBits(bitset: BitSet): Boolean {
        val bytes = eeprom!!.getBytes()!!
        if (bitset.updateValue(bytes)) {
            eeprom!!.touch(bitset.offset, bytes.size)
        }
        return true
    }

    companion object {
        private const val D = false
        private const val TAG = "ECM"
        private const val UNKNOWN = "N/A"
        private const val PAGE_ZERO_OFFSET = 0xFF // Page 0 always starts at 0xFF

        // List of EEPROM values that will be written to page 0
        private val PAGE_ZERO_VARS_TO_WRITE = arrayOf(
            Variables.LFuel1,
            Variables.KBaro,
        )

        /**
         * Returns the single `ECM` instance.
         *
         * @param ctx the Android Context
         */
        @JvmStatic
        fun getInstance(ctx: Context): ECM = AppContainer.from(ctx).ecm
    }
}
