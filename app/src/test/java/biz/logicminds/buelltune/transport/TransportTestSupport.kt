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
package biz.logicminds.buelltune.transport

import biz.logicminds.buelltune.PDU
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.util.Collections

/** Builds an ACK response PDU carrying [data] as its EEPROM payload. */
internal fun ackWithBytes(data: ByteArray): PDU {
    val payload = ByteArray(1 + data.size)
    payload[0] = PDU.ACK
    System.arraycopy(data, 0, payload, 1, data.size)
    return PDU(PDU.getECMID(), PDU.DROID_ID, payload)
}

/**
 * Minimal protocol-aware fake ECM: accepts one loopback TCP connection,
 * parses each request as a [PDU] using the exact SOH/EOH/SOT/EOT framing
 * [PduFraming] expects, records it, and replies with whatever [respond]
 * returns for it - or, if [respond] returns `null`, closes the connection
 * *without* responding, simulating a server-side link drop mid-request.
 *
 * Reused both directly by [TcpTransport] tests (a real `Socket` connects to
 * [port]) and, wrapped behind a mocked `BluetoothSocket`, by
 * [BluetoothClassicTransport] tests - giving both transports' contract
 * tests real, blocking byte-stream I/O rather than a hand-rolled fake
 * stream.
 */
class FakePduServer(private val respond: (PDU) -> PDU?) : AutoCloseable {
    private val serverSocket = ServerSocket(0)
    val port: Int get() = serverSocket.localPort
    val requests: MutableList<PDU> = Collections.synchronizedList(mutableListOf())

    private val thread = Thread {
        try {
            serverSocket.accept().use { socket ->
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                while (!socket.isClosed) {
                    val request = try {
                        readPdu(input)
                    } catch (e: IOException) {
                        break
                    }
                    requests.add(request)
                    val response = respond(request) ?: break
                    writePdu(output, response)
                }
            }
        } catch (e: IOException) {
            // Server or client socket closed -- test is done with us.
        }
    }.apply {
        isDaemon = true
        start()
    }

    override fun close() {
        serverSocket.close()
        thread.join(2000)
    }

    private fun readPdu(input: InputStream): PDU {
        val header = ByteArray(6)
        readFully(input, header, 0, 6)
        val len = header[3].toInt() and 0xff
        val full = ByteArray(len + 7)
        System.arraycopy(header, 0, full, 0, 6)
        readFully(input, full, 6, len + 1)
        return PDU(full, len + 7)
    }

    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int, len: Int) {
        var r = 0
        while (r < len) {
            val n = input.read(buffer, offset + r, len - r)
            if (n == -1) throw IOException("EOF while reading fake ECM request")
            r += n
        }
    }

    private fun writePdu(output: OutputStream, pdu: PDU) {
        output.write(pdu.getBytes())
        output.flush()
    }
}
