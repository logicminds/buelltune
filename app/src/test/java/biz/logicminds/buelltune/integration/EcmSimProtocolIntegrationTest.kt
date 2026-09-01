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
package biz.logicminds.buelltune.integration

import biz.logicminds.buelltune.Constants
import biz.logicminds.buelltune.ECM
import biz.logicminds.buelltune.PDU
import biz.logicminds.buelltune.transport.ConnectionState
import biz.logicminds.buelltune.transport.FailureCause
import biz.logicminds.buelltune.transport.PduFraming
import biz.logicminds.buelltune.transport.TcpTransport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.sql.Connection

/**
 * The protocol-surface half of R16/AE5: connect/version handshake, EEPROM
 * page fetch, an EEPROM write command, realtime polling, an active-test
 * trigger, and a checksum-corrupted-frame rejection -- all driven against
 * one real `ecmsim` process shared for the whole class ([sim] is a
 * `@ClassRule`; `ecmsim` serves one connection at a time in a loop, so
 * each `@Test` opens and closes its own fresh TCP connection against it,
 * per U9's own verification notes). The connection-loss scenarios (R17)
 * live in [EcmSimConnectionLossIntegrationTest] instead, since those need
 * a disposable simulator process per test.
 */
@Category(EcmSimIntegrationSuite::class)
class EcmSimProtocolIntegrationTest {

    companion object {
        @ClassRule
        @JvmField
        val sim = EcmSimRule("BUEIB")
    }

    private lateinit var connection: Connection

    @Before
    fun openDbConnection() {
        connection = AssetDatabase.newConnection()
    }

    @After
    fun closeDbConnection() {
        connection.close()
    }

    private fun newEcm(): ECM = ECM(
        JdbcVariableProvider(connection),
        JdbcBitSetProvider(connection),
        JdbcEcmDefinitionsProvider(connection),
        null,
    )

    private fun newTcpTransport(port: Int = sim.port) = TcpTransport {
        Socket().apply { connect(InetSocketAddress(sim.host, port), TcpTransport.CONNECT_TIMEOUT_MS) }
    }

    @Test
    fun connectAndReadVersionResolvesToBueibDdfi2() {
        val ecm = newEcm()
        ecm.connect(newTcpTransport(), ECM.Protocol.STOCK)
        try {
            val version = ecm.setupEEPROM()
            assertTrue("expected version to start with BUEIB, got '$version'", version.startsWith("BUEIB"))
            assertEquals(ECM.Type.DDFI2, ecm.getType())
            assertEquals("BUEIB", ecm.getId())
        } finally {
            ecm.disconnect()
        }
    }

    @Test
    fun fetchAllEepromPagesAssemblesFullByteCountFromDefinitions() {
        val ecm = newEcm()
        ecm.connect(newTcpTransport(), ECM.Protocol.STOCK)
        try {
            ecm.setupEEPROM()
            val eeprom = ecm.getEEPROM()!!

            // Cross-check against the definitions database directly, not
            // just EEPROM's own bookkeeping -- fromPageRows() derives
            // length()/xsize from the SAME db row this queries.
            val fromDb = JdbcEcmDefinitionsProvider(connection).getEeprom("BUEIB")!!
            assertEquals(fromDb.length(), eeprom.length())
            assertEquals(1210, eeprom.length())
            assertEquals(7, eeprom.getPageCount())
            assertEquals(eeprom.length(), eeprom.getPages().sumOf { it.length() })

            for (page in eeprom.getPages()) {
                ecm.readEEPromPage(page)
            }

            val bytes = eeprom.getBytes()!!
            assertEquals("assembled EEPROM byte count must match the definition database's page layout", eeprom.length(), bytes.size)
        } finally {
            ecm.disconnect()
        }
    }

    @Test
    fun writeEepromPageZeroSendsAckedSelectiveWriteRequestsForResolvedVars() {
        val ecm = newEcm()
        val relay = PduRelay(sim.host, sim.port)
        relay.start()
        val transport = newTcpTransport(relay.localPort)
        try {
            ecm.connect(transport, ECM.Protocol.STOCK)
            ecm.setupEEPROM()
            val eeprom = ecm.getEEPROM()!!
            for (page in eeprom.getPages()) ecm.readEEPromPage(page)
            val page0 = eeprom.getPage(0)!!

            // ECM.writeEEPromPage(page0) only issues a request for each of
            // Variables.LFuel1/KBaro that actually resolves for this ECM's
            // category (BUEIB's reference-DB row has no LFuel1 entry in its
            // category -- confirmed by direct query -- so exactly one
            // write is expected here, not two; asserted generically against
            // whatever the definitions database resolves rather than a
            // hardcoded count).
            val variableProvider = JdbcVariableProvider(connection)
            val candidateVars = listOf(Constants.Variables.LFuel1, Constants.Variables.KBaro)
                .mapNotNull { variableProvider.getEEPROMVariable("BUEIB", it) }
            assertTrue("expected at least one page-zero variable to resolve for BUEIB", candidateVars.isNotEmpty())

            val requestsBefore = relay.requests.size
            // ecmsim's CMD_SET handler ACKs without persisting (U9's own
            // finding) -- writeEEPromPage() throwing IOException would
            // already fail this test on a NACK/missing-ACK response, so a
            // clean return *is* the ACK exchange; read-back is deliberately
            // not asserted (a documented bound, not a coverage gap, per the
            // plan's U10 approach step 4).
            ecm.writeEEPromPage(page0)

            val writeRequests = relay.requests.drop(requestsBefore)
            assertEquals(candidateVars.size, writeRequests.size)
            for (request in writeRequests) {
                assertEquals("expected the CMD_SET opcode", PDU.CMD_SET, request.getPayload()[0].toInt() and 0xff)
                assertEquals("expected a page-0 (selective) write", 0, request.getPageNr())
            }
            // ECM.writeEEPromPage's page-0 branch sends offset = (0xFF +
            // v.offset + 1) & 0xff -- 0xFF is ECM.PAGE_ZERO_OFFSET (private,
            // duplicated here as a literal since it isn't exposed).
            val expectedOffsets = candidateVars.map { (0xFF + it.offset + 1) and 0xff }.toSet()
            val actualOffsets = writeRequests.map { it.getPageOffset() }.toSet()
            assertEquals(expectedOffsets, actualOffsets)
            assertFalse("page should be marked saved (not touched) after a successful write", page0.isTouched())
        } finally {
            ecm.disconnect()
            relay.close()
        }
    }

    @Test
    fun pollRuntimeDataAcrossFiveFramesDecodesPlausibleVariables() {
        val ecm = newEcm()
        ecm.connect(newTcpTransport(), ECM.Protocol.STOCK)
        try {
            val id = ecm.setupEEPROM()
            assertTrue(id.startsWith("BUEIB"))

            val names = JdbcVariableProvider(connection).getRtVariableNames("BUEIB")
            assertTrue("expected at least one runtime variable definition", names.isNotEmpty())

            repeat(5) { frameIndex ->
                ecm.readRTData()
                var decoded = 0
                for (name in names) {
                    // Must not throw for any defined variable, and must
                    // resolve to a Variable -- the "no parse exception"
                    // half of R16's polling scenario.
                    if (ecm.getRuntimeValue(name) != null) {
                        decoded++
                    }
                }
                assertTrue("frame $frameIndex: expected at least one decoded Variable, got $decoded", decoded > 0)
            }

            // RPM specifically decodes to a plausible value from a real
            // captured log (not random/generated data): a Buell engine
            // never spins anywhere near 20000 RPM.
            val rpm = ecm.getRuntimeValue("RPM")
            assertNotNull("expected RPM to resolve to a Variable definition", rpm)
            val rpmValue = rpm!!.getIntValue()
            assertTrue("RPM=$rpmValue is not a plausible engine speed", rpmValue in 0..20000)
        } finally {
            ecm.disconnect()
        }
    }

    @Test
    fun activeTestTriggerIsAcknowledgedAndReflectedInDeviceTestState() {
        val ecm = newEcm()
        ecm.connect(newTcpTransport(), ECM.Protocol.STOCK)
        try {
            ecm.setupEEPROM()
            // runTest() issues the page-32 (0x20) virtual-page write
            // ecmsim recognizes as a device-test trigger (PDU.commandRequest
            // -> CMD_SET, page 0x20); it throws IOException("Test failed.")
            // if the response isn't an ACK, so a clean return already is
            // the acknowledgement.
            ecm.runTest(PDU.Function.FuelPump)
            // getCurrentState()/isBusy() GET the SAME virtual page 32 --
            // ecmsim reports it "busy" for 3s after a trigger, giving a
            // second, independent confirmation that the simulator's device
            // test state actually flipped, not just that the write ACKed.
            assertTrue("expected the simulator to report the device test as active", ecm.isBusy())
        } finally {
            ecm.disconnect()
        }
    }

    @Test(timeout = 15_000)
    fun checksumCorruptedResponseIsRejectedNotHungAndDoesNotDesyncTheSimulator() {
        val relay = PduRelay(sim.host, sim.port, corruptNextResponse = true)
        relay.start()
        val ecm = newEcm()
        val transport = newTcpTransport(relay.localPort)
        try {
            ecm.connect(transport, ECM.Protocol.STOCK)
            val started = System.currentTimeMillis()
            // readVersion() is the very first request on this fresh
            // connection -- its response is the one the relay corrupts
            // (flips the trailing checksum byte) verbatim.
            val thrown = assertThrows(IOException::class.java) { ecm.readVersion() }
            val elapsedMs = System.currentTimeMillis() - started
            assertTrue(
                "must reject promptly, not hang past PduFraming's response timeout (${elapsedMs}ms elapsed)",
                elapsedMs < PduFraming.RESPONSE_TIMEOUT_MS + 5000,
            )
            assertNotNull(thrown.message)

            val state = transport.state.value
            assertTrue("expected Failed(Io), got $state", state is ConnectionState.Failed && state.cause is FailureCause.Io)
        } finally {
            relay.close()
        }

        // Resync proof: the corrupted exchange never touched ecmsim's own
        // socket handling (only the bytes the relay forwarded were
        // mutated) -- a brand-new connection straight to the real
        // simulator completes a normal handshake immediately afterward.
        val ecm2 = newEcm()
        ecm2.connect(newTcpTransport(), ECM.Protocol.STOCK)
        try {
            assertTrue(ecm2.setupEEPROM().startsWith("BUEIB"))
        } finally {
            ecm2.disconnect()
        }
    }
}
