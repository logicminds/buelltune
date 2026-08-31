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

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.text.format.DateFormat
import android.util.Log

import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager

import biz.logicminds.buelltune.Constants.DataSource
import biz.logicminds.buelltune.Constants.Variables
import biz.logicminds.buelltune.EEPROM.Page
import biz.logicminds.buelltune.Error.ErrorType
import biz.logicminds.buelltune.PDU.Function

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.ParseException
import java.util.Calendar
import java.util.LinkedList
import java.util.UUID

import de.kai_morich.simple_bluetooth_le_terminal.SerialListener
import de.kai_morich.simple_bluetooth_le_terminal.SerialSocket

/**
 * This class represents the main interface to your Buell ECM. Communication
 * with the ECM may take place via a Bluetooth SPP adapter or TCP/IP. Functions
 * include:
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
 * Use [ECM.getInstance] as a starting point and call one of the connect()
 * methods for establishing a connection to the ECM. Before EEPROM data can
 * be accessed, you must call [ECM.setupEEPROM].
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

    private val mReceiveBuffer = ByteArray(256)
    private var usbIoManager: SerialInputOutputManager? = null
    private var connected = false
    private var socket: Any? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var eeprom: EEPROM? = null
    private var recording = false
    private var protocol = Protocol.STOCK

    /** getRuntimeData()/setRuntimeData(byte[]) -- Kotlin's auto-generated accessors for this name already match. */
    var runtimeData: ByteArray? = null

    // -------------------------------------------------------------------
    // Transport layer (U7 territory). The four connect() overloads below,
    // disconnect(), and the inputStream/outputStream/socket/usbIoManager
    // fields above are the seam U7 extracts into EcmTransport (Bluetooth
    // Classic, BLE, USB-serial, TCP). U6 ports this half verbatim and marks
    // it; U7 (a later, separate unit) is what actually cuts it out from
    // behind the protocol methods further down this file.
    // -------------------------------------------------------------------

    /**
     * Connect to given Bluetooth Classic Serial Port Profile (SPP)
     *
     * @param bluetoothDevice the bluetooth modem
     */
    @Throws(IOException::class)
    fun connect(bluetoothDevice: BluetoothDevice, protocol: Protocol) {
        var s: BluetoothSocket? = null
        try {
            s = bluetoothDevice.createRfcommSocketToServiceRecord(uuid)
            if (s != null) {
                s.connect()
                if (D) Log.d(TAG, "Max receive: ${s.maxReceivePacketSize}, max transmit: ${s.maxTransmitPacketSize}")
                inputStream = s.inputStream
                outputStream = s.outputStream
                socket = s
            }
        } catch (e: IOException) {
            Log.w(TAG, "Unable to connect. ", e)
            if (socket != null) {
                try {
                    s?.close()
                } catch (e1: IOException) {
                }
                socket = null
            }
            throw e
        }
        this.protocol = protocol
        PDU.setProtocol(protocol)
        connected = true
    }

    @Throws(IOException::class)
    fun connect(uart: UsbSerialPort, protocol: Protocol) {
        try {
            val uartOutPipe = PipedOutputStream()
            this.inputStream = PipedInputStream(uartOutPipe)
            usbIoManager = SerialInputOutputManager(
                uart,
                object : SerialInputOutputManager.Listener {
                    /**
                     * SerialInputOutputManager Listener functions
                     */
                    override fun onNewData(data: ByteArray) {
                        try {
                            uartOutPipe.write(data)
                        } catch (e: IOException) {
                            Log.e(TAG, "IO Exception while trying to write to UART", e)
                            throw RuntimeException(e)
                        }
                    }

                    override fun onRunError(e: Exception) {
                        Log.e(TAG, "UART read/write run error", e)
                    }
                },
            )
            this.outputStream = object : OutputStream() {
                override fun write(i: Int) {
                    write(byteArrayOf(i.toByte()))
                }

                override fun write(b: ByteArray) {
                    uart.write(b, 2000)
                }
            }
            usbIoManager?.start()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to connect to USB UART", e)
            throw e
        }
        this.protocol = protocol
        PDU.setProtocol(protocol)
        connected = true
    }

    /**
     * Connect to BLE device
     */
    @Throws(IOException::class)
    fun connect(ctx: Context, device: BluetoothDevice, protocol: Protocol) {
        val s = SerialSocket(ctx, device)
        val monitor = Any()
        val out = PipedOutputStream()
        val pipedIn = PipedInputStream(out)
        connected = false

        s.connect(object : SerialListener {
            override fun onSerialConnect() {
                Log.i(TAG, "BLE connected!")
                connected = true
                synchronized(monitor) {
                    (monitor as java.lang.Object).notify()
                }
            }

            override fun onSerialConnectError(e: Exception) {
                Log.e(TAG, "BLE connect error!", e)
                synchronized(monitor) {
                    (monitor as java.lang.Object).notify()
                }
            }

            override fun onSerialRead(data: ByteArray) {
                // Log.d(TAG, "On Serial Read: " + Utils.hexdump(data));
                try {
                    out.write(data)
                } catch (e: IOException) {
                    Log.e(TAG, "out.write failed")
                }
            }

            override fun onSerialIoError(e: Exception) {
                Log.e(TAG, "On Serial IO Error", e)
                try {
                    out.close()
                } catch (ioe: IOException) {
                    Log.w(TAG, "Error closing BLE pipe", ioe)
                }
                handleConnectionLost(e)
            }
        })
        synchronized(monitor) {
            try {
                if (D) Log.d(TAG, "Waiting for BLE connection...")
                (monitor as java.lang.Object).wait()
            } catch (e: InterruptedException) {
                if (D) Log.d(TAG, "Interrupted")
            }
        }
        if (!connected) {
            throw IOException("BLE connection failed")
        }

        this.protocol = protocol
        PDU.setProtocol(this.protocol)
        this.inputStream = pipedIn
        this.outputStream = object : OutputStream() {
            override fun write(i: Int) {
                s.write(byteArrayOf(i.toByte()))
            }

            override fun write(b: ByteArray) {
                s.write(b)
            }
        }
        this.socket = s
    }

    /**
     * Connect via TCP
     *
     * @param host host name
     * @param port port
     */
    @Throws(IOException::class)
    fun connect(host: String, port: Int, protocol: Protocol) {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), TCP_CONNECT_TIMEOUT)
        inputStream = s.inputStream
        outputStream = s.outputStream
        socket = s
        this.protocol = protocol
        PDU.setProtocol(protocol)
        connected = true
    }

    /**
     * Disconnect from the ECM
     */
    @Synchronized
    @Throws(IOException::class)
    fun disconnect() {
        if (connected) {
            try {
                val sock = socket
                if (sock != null) {
                    try {
                        when (sock) {
                            is BluetoothSocket -> sock.close()
                            is Socket -> sock.close()
                            is SerialSocket -> sock.disconnect()
                        }
                    } finally {
                        socket = null
                    }
                } else {
                    // USB uart
                    try {
                        usbIoManager?.stop()
                    } finally {
                        usbIoManager = null
                    }
                }
            } finally {
                inputStream = null
                outputStream = null
                connected = false
                runtimeData = null
            }
        }
    }

    /**
     * Marks the connection as lost and releases any underlying transport
     * resources. Invoked internally when I/O to the ECM fails unexpectedly
     * (e.g. the Bluetooth link is dropped or goes out of range), as opposed
     * to a user-initiated [disconnect].
     */
    @Synchronized
    private fun handleConnectionLost(cause: Exception) {
        if (!connected) {
            return
        }
        Log.w(TAG, "Connection to ECM lost: $cause", cause)
        try {
            disconnect()
        } catch (ioe: IOException) {
            Log.w(TAG, "Error releasing broken connection", ioe)
        }
    }

    // -------------------------------------------------------------------
    // Protocol layer. Stays in ECM after U7 cuts the transport seam above
    // out into EcmTransport; only the streams these methods read/write move
    // behind that interface.
    // -------------------------------------------------------------------

    /**
     * Send a protocol data unit (PDU) to the ECM and return the ECMs response
     */
    @Synchronized
    @Throws(IOException::class)
    private fun sendPDU(pdu: PDU): PDU {
        try {
            if (D) Log.d(TAG, "Sending: $pdu")
            val out = outputStream ?: throw IOException("OutputStream to RFCOMM not available.")
            val bytes = pdu.getBytes()
            out.write(bytes)
            // Wait for response
            val ret = receivePDU()
            if (!ret.isResponse()) {
                throw IOException("No valid response from ECM (wrong Protocol?)")
            }
            if (!ret.isACK()) {
                throw IOException("Request not acknowledged by ECM (error code ${ret.getErrorIndicator()}).")
            }
            return ret
        } catch (ioe: IOException) {
            Log.e(TAG, "IO Exception sending PDU", ioe)
            handleConnectionLost(ioe)
            throw ioe
        } catch (rte: RuntimeException) {
            Log.e(TAG, "Runtime Exception sending PDU", rte)
            throw rte
        }
    }

    @Throws(IOException::class)
    fun receivePDU(): PDU {
        try {
            read(mReceiveBuffer, 0, 6, DEFAULT_TIMEOUT)
            if (mReceiveBuffer[0] != PDU.SOH && mReceiveBuffer[4] != PDU.EOH && mReceiveBuffer[5] != PDU.SOT) {
                throw IOException("Invalid Header received.")
            }
            val len = mReceiveBuffer[3].toInt() and 0xff
            // Log.d(TAG, "Start of PDU: " + Utils.hexdump(mReceiveBuffer, 0, 6));
            read(mReceiveBuffer, 6, len + 1, DEFAULT_TIMEOUT)
            val response: PDU
            try {
                response = PDU(mReceiveBuffer, len + 7)
            } catch (e: ParseException) {
                throw IOException("Unable to parse incoming PDU. " + e.localizedMessage)
            }
            if (D) Log.d(TAG, "Received: $response")
            return response
        } catch (ioe: IOException) {
            Log.e(TAG, "I/O Exception receiving PDU. " + ioe.message)
            // Drain receive buffer, we might be out-of-sync
            try {
                while (inputStream!!.available() > 0) {
                    inputStream!!.read()
                }
            } catch (e: IOException) {
            }
            throw ioe
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

    @Throws(IOException::class)
    private fun read(buffer: ByteArray, offset: Int, len: Int, timeoutMs: Int): Int {
        if (offset + len >= buffer.size) {
            throw IOException("${offset + len}: Array index out of bounds.")
        }
        var timeout = timeoutMs
        var r = 0
        while (r < len && timeout > 0) {
            if (inputStream!!.available() > 0) {
                do {
                    val toRead = minOf(len - r, inputStream!!.available())
                    try {
                        val i = inputStream!!.read(buffer, r + offset, toRead)
                        if (i == -1) {
                            throw IOException("EOF while reading $toRead/$len bytes at offset ${r + offset}")
                        }
                        r += i
                    } catch (rte: RuntimeException) {
                        throw IOException("Runtime Exception while reading $toRead bytes at offset ${r + offset}")
                    }
                } while (r < len && inputStream!!.available() > 0)
            } else {
                try {
                    Thread.sleep(10)
                    timeout -= 10
                } catch (e: InterruptedException) {
                }
            }
        }
        if (r != len) {
            throw IOException("Timeout reading $r from $len bytes.")
        }
        return r
    }

    /**
     * Indicates if a connection to the underlying ECM is established.
     */
    fun isConnected(): Boolean = connected

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
        private const val DEFAULT_TIMEOUT = 1000
        private const val TCP_CONNECT_TIMEOUT = 5000
        private const val TAG = "ECM"
        private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
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
