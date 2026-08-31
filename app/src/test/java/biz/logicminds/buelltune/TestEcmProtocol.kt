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

import biz.logicminds.buelltune.data.EepromPageRow
import biz.logicminds.buelltune.transport.TransportFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket

/**
 * Exercises ECM's protocol layer (R5, R8) over [TransportFactory.tcp] -- the
 * one transport path with zero Android dependency, so the still-verbatim-
 * ported protocol code (`sendPDU`/`readRTData`/`readEEPromPage`/
 * `writeEEPromPage`) can be driven from a pure JVM test. Originally pinned
 * ahead of U7's transport extraction against the raw `ECM.connect(host,
 * port, protocol)` overload; now exercises the real `EcmTransport` path
 * those overloads were replaced by, with the same scenarios unchanged.
 *
 * A minimal protocol-aware fake ECM ([FakeEcmServer]) speaks the real wire
 * framing (SOH/EOH/SOT/EOT + XOR checksum) via [PDU] on the server side of a
 * loopback socket, so the exact request sequence ECM issues can be captured
 * and asserted.
 */
class TestEcmProtocol {

    private fun newEcm(): ECM = ECM(NoOpVariableProvider(), NoOpBitSetProvider(), NoOpDefinitionsProvider(), null)

    @Test
    fun ioExceptionDuringReadRTDataLeavesEcmDisconnected() {
        ServerSocket(0).use { server ->
            val ecm = newEcm()

            // Accept the connection and immediately drop it server-side --
            // ECM's write may succeed locally, but it will never see a
            // response and must eventually surface an IOException.
            val acceptThread = Thread {
                server.accept().close()
            }
            acceptThread.start()

            ecm.connect(TransportFactory.tcp("127.0.0.1", server.localPort), ECM.Protocol.STOCK)
            acceptThread.join(2000)
            assertTrue("connect() should have succeeded", ecm.isConnected())

            var threw = false
            try {
                ecm.readRTData()
            } catch (e: IOException) {
                threw = true
            }
            assertTrue("readRTData() should have thrown IOException once the link was dropped", threw)
            assertFalse("ECM should mark itself disconnected (f3337a1 contract, R8)", ecm.isConnected())
        }
    }

    @Test
    fun readEEPromPageChunksNonZeroPageIn16ByteRequests() {
        val pageLength = 40 // -> 16, 16, 8
        val eeprom = EEPROM("TEST")
        eeprom.setBytes(ByteArray(pageLength))
        val page = eeprom.Page(3, pageLength)
        page.setStart(0)
        eeprom.addPage(page)

        FakeEcmServer { request -> ackWithBytes(ByteArray(request.getBytes()[9].toInt() and 0xff)) }.use { server ->
            val ecm = newEcm()
            ecm.connect(TransportFactory.tcp("127.0.0.1", server.port), ECM.Protocol.STOCK)
            ecm.readEEPromPage(page)

            val expectedOffsets = listOf(0, 16, 32)
            val expectedLens = listOf(16, 16, 8)
            assertEquals(3, server.requests.size)
            server.requests.forEachIndexed { i, req ->
                assertEquals(3, req.getPageNr())
                assertEquals(expectedOffsets[i], req.getPageOffset())
                assertEquals(expectedLens[i], req.getBytes()[9].toInt() and 0xff)
            }
        }
    }

    @Test
    fun readEEPromPagePageZeroReadsOneByteAtATime() {
        // Page 0 is 3 bytes, sitting at the tail of a 10-byte EEPROM image.
        val eeprom = EEPROM("TEST")
        eeprom.setBytes(ByteArray(10))
        val page = eeprom.Page(0, 3)
        page.setStart(7)
        eeprom.addPage(page)

        FakeEcmServer { ackWithBytes(byteArrayOf(0)) }.use { server ->
            val ecm = newEcm()
            ecm.connect(TransportFactory.tcp("127.0.0.1", server.port), ECM.Protocol.STOCK)
            ecm.readEEPromPage(page)

            // offset = PAGE_ZERO_OFFSET(0xFF) - page.length() + i + 1, dtr always 1
            val expectedOffsets = listOf(253, 254, 255)
            assertEquals(3, server.requests.size)
            server.requests.forEachIndexed { i, req ->
                assertEquals(0, req.getPageNr())
                assertEquals(expectedOffsets[i], req.getPageOffset())
                assertEquals(1, req.getBytes()[9].toInt() and 0xff)
            }
        }
    }

    @Test
    fun writeEEPromPageZeroWritesOnlySelectedFields() {
        // xsize == length so hasPageZero() is true and the negative-offset
        // page-zero variables resolve, matching real LFuel1/KBaro definitions.
        val eeprom = EEPROM.fromPageRows("TEST", listOf(EepromPageRow(xsize = 10, type = "DDFI-2", page = 0, pgsize = 4)))!!
        eeprom.setBytes(ByteArray(10))
        val page = eeprom.getPage(0)!!

        val vars = mapOf(
            Constants.Variables.LFuel1 to stubVariable(offset = -2, size = 1),
            Constants.Variables.KBaro to stubVariable(offset = -1, size = 1),
        )

        FakeEcmServer { ackWithBytes(ByteArray(0)) }.use { server ->
            val ecm = ECM(StubVariableProvider(vars), NoOpBitSetProvider(), NoOpDefinitionsProvider(), null)
            ecm.setEEPROM(eeprom)
            ecm.connect(TransportFactory.tcp("127.0.0.1", server.port), ECM.Protocol.STOCK)

            ecm.writeEEPromPage(page)

            // PAGE_ZERO_OFFSET(0xFF) + var.offset + 1, in PAGE_ZERO_VARS_TO_WRITE order (LFuel1, KBaro)
            assertEquals(2, server.requests.size)
            assertEquals(0, server.requests[0].getPageNr())
            assertEquals(254, server.requests[0].getPageOffset())
            assertEquals(0, server.requests[1].getPageNr())
            assertEquals(255, server.requests[1].getPageOffset())
        }
    }

    @Test
    fun writeEEPromPageNonZeroWritesFullPageIn16ByteChunks() {
        val pageLength = 40 // -> 16, 16, 8
        val eeprom = EEPROM("TEST")
        eeprom.setBytes(ByteArray(pageLength))
        val page = eeprom.Page(2, pageLength)
        page.setStart(0)
        eeprom.addPage(page)

        FakeEcmServer { ackWithBytes(ByteArray(0)) }.use { server ->
            val ecm = newEcm()
            ecm.connect(TransportFactory.tcp("127.0.0.1", server.port), ECM.Protocol.STOCK)
            ecm.writeEEPromPage(page)

            val expectedOffsets = listOf(0, 16, 32)
            assertEquals(3, server.requests.size)
            server.requests.forEachIndexed { i, req ->
                assertEquals(2, req.getPageNr())
                assertEquals(expectedOffsets[i], req.getPageOffset())
            }
            assertFalse(page.isTouched())
        }
    }

    // -------------------------------------------------------------------
    // Test doubles
    // -------------------------------------------------------------------

    private fun stubVariable(offset: Int, size: Int): Variable {
        val v = Variable()
        v.offset = offset
        v.size = size
        v.width = size
        v.type = Variable.DataType.SCALAR
        v.init()
        return v
    }

    private fun ackWithBytes(data: ByteArray): PDU {
        val payload = ByteArray(1 + data.size)
        payload[0] = PDU.ACK
        System.arraycopy(data, 0, payload, 1, data.size)
        return PDU(PDU.getECMID(), PDU.DROID_ID, payload)
    }

    private class NoOpVariableProvider : VariableProvider() {
        override fun getRtVariableNames(ecm: String): Collection<String> = emptyList()
        override fun getRtVariable(ecm: String, name: String): Variable? = null
        override fun getScalarRtVariableNames(ecm: String): Collection<String> = emptyList()
        override fun getBitfieldRtVariableNames(ecm: String): Collection<String> = emptyList()
        override fun getEEPROMVariable(ecm: String, name: String): Variable? = null
        override fun getName(varname: String): String? = null
        override fun getName(varname: String, bitnumber: Int): String? = null
        override fun getNearestEEPROMVariable(ecm: String, offset: Int): Variable? = null
    }

    private class StubVariableProvider(private val byName: Map<String, Variable>) : VariableProvider() {
        override fun getRtVariableNames(ecm: String): Collection<String> = emptyList()
        override fun getRtVariable(ecm: String, name: String): Variable? = null
        override fun getScalarRtVariableNames(ecm: String): Collection<String> = emptyList()
        override fun getBitfieldRtVariableNames(ecm: String): Collection<String> = emptyList()
        override fun getEEPROMVariable(ecm: String, name: String): Variable? = byName[name]
        override fun getName(varname: String): String? = null
        override fun getName(varname: String, bitnumber: Int): String? = null
        override fun getNearestEEPROMVariable(ecm: String, offset: Int): Variable? = null
    }

    private class NoOpBitSetProvider : BitSetProvider() {
        override fun getBitSet(ecmId: String, name: String, source: Constants.DataSource): BitSet? = null
    }

    private class NoOpDefinitionsProvider : EcmDefinitionsProvider {
        override fun getEeprom(ecmId: String): EEPROM? = null
        override fun size2id(length: Int): List<String> = emptyList()
    }

    /**
     * Minimal protocol-aware fake ECM: accepts one loopback connection,
     * parses each request as a [PDU] using the exact SOH/EOH/SOT/EOT framing
     * `ECM.receivePDU`/`ECM.read` expect, records it, and replies with
     * whatever [respond] returns.
     */
    private class FakeEcmServer(private val respond: (PDU) -> PDU) : AutoCloseable {
        private val serverSocket = ServerSocket(0)
        val port: Int get() = serverSocket.localPort
        val requests: MutableList<PDU> = java.util.Collections.synchronizedList(mutableListOf())

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
                        writePdu(output, respond(request))
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
}
