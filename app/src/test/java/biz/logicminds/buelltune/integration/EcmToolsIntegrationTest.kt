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
import biz.logicminds.buelltune.chat.EcmTools
import biz.logicminds.buelltune.chat.ToolResult
import biz.logicminds.buelltune.transport.TcpTransport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category
import java.net.InetSocketAddress
import java.net.Socket
import java.sql.Connection

/**
 * U4: proves the same six [EcmTools] methods the chat agent is allowed to
 * call (R1, R3, R8, KTD2, KTD4) against a real simulated ECM over TCP,
 * mirroring [EcmSimProtocolIntegrationTest]'s connect/`setupEEPROM()`/
 * fetch-all-pages/try-finally-disconnect discipline exactly rather than
 * inventing a second pattern. [EcmSimProtocolIntegrationTest] already
 * covers the raw protocol surface (version handshake, page fetch, RT
 * polling) in depth -- this class only exercises the [EcmTools] facade
 * layered on top of it, so assertions here stay narrow: "did the tool
 * return the right [ToolResult] shape", not "did the wire protocol decode
 * correctly" (already proven elsewhere).
 */
@Category(EcmSimIntegrationSuite::class)
class EcmToolsIntegrationTest {

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

    private fun newTools(ecm: ECM): EcmTools =
        EcmTools(ecm, JdbcVariableProvider(connection), JdbcEcmDefinitionsProvider(connection))

    /**
     * Connects, runs `setupEEPROM()`, and fetches every EEPROM page,
     * mirroring [EcmSimProtocolIntegrationTest.fetchAllEepromPagesAssemblesFullByteCountFromDefinitions].
     * Also flips [biz.logicminds.buelltune.EEPROM.setEepromRead] once every
     * page is in, matching `FetchTask`'s own post-fetch call
     * (`app/src/main/java/biz/logicminds/buelltune/task/FetchTask.java:81`)
     * -- `EcmTools.getEepromParameter`/`getFuelMapRegion` gate on that same
     * flag (R7/U2), so skipping it here would strand both EEPROM-dependent
     * tools on the "not yet read" path even after a full fetch.
     */
    private fun connectAndFetchAllPages(ecm: ECM) {
        ecm.connect(newTcpTransport(), ECM.Protocol.STOCK)
        ecm.setupEEPROM()
        val eeprom = ecm.getEEPROM()!!
        for (page in eeprom.getPages()) {
            ecm.readEEPromPage(page)
        }
        eeprom.setEepromRead(true)
    }

    @Test
    fun getEcmInfo_reportsBueibIdAndDdfi2Type() {
        val ecm = newEcm()
        ecm.connect(newTcpTransport(), ECM.Protocol.STOCK)
        try {
            ecm.setupEEPROM()
            // Cross-check against ecm.getType() directly, matching
            // EcmSimProtocolIntegrationTest.connectAndReadVersionResolvesToBueibDdfi2's
            // own assertion -- get_ecm_info's payload has no dedicated
            // "type" field (it reports ecmId/version/protocol/transport),
            // so the DDFI2 claim is verified against the same ECM state
            // the tool call reads from.
            assertEquals(ECM.Type.DDFI2, ecm.getType())

            val result = runBlocking { newTools(ecm).getEcmInfo() }
            assertTrue("expected Ok, got $result", result is ToolResult.Ok)
            val payload = (result as ToolResult.Ok).payload

            val ecmId = payload["ecmId"]?.jsonPrimitive?.content
            assertNotNull("expected an ecmId in the payload", ecmId)
            assertTrue("expected ecmId to start with BUEIB, got '$ecmId'", ecmId!!.startsWith("BUEIB"))

            val version = payload["version"]?.jsonPrimitive?.content
            assertNotNull("expected a version string in the payload", version)
            assertTrue("expected version to start with BUEIB, got '$version'", version!!.startsWith("BUEIB"))
        } finally {
            ecm.disconnect()
        }
    }

    @Test
    fun listLiveVariables_returnsNonEmptyCatalogWithUnits() {
        val ecm = newEcm()
        ecm.connect(newTcpTransport(), ECM.Protocol.STOCK)
        try {
            ecm.setupEEPROM()
            val result = runBlocking { newTools(ecm).listLiveVariables() }
            assertTrue("expected Ok, got $result", result is ToolResult.Ok)
            val variables = (result as ToolResult.Ok).payload["variables"]!!.jsonArray
            assertTrue("expected a non-empty runtime variable catalog", variables.isNotEmpty())
            for (entry in variables) {
                val obj = entry.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content
                assertNotNull("expected every catalog entry to carry a name", name)
                assertFalse("expected a non-blank variable name", name!!.isBlank())
                assertTrue("expected a 'unit' key on entry for '$name'", obj.containsKey("unit"))
            }
        } finally {
            ecm.disconnect()
        }
    }

    @Test
    fun readLiveData_forRpmAndAfv_returnsPlausibleValues() {
        val ecm = newEcm()
        ecm.connect(newTcpTransport(), ECM.Protocol.STOCK)
        try {
            ecm.setupEEPROM()
            val result = runBlocking { newTools(ecm).readLiveData(listOf("RPM", "AFV")) }
            assertTrue("expected Ok, got $result", result is ToolResult.Ok)
            val values = (result as ToolResult.Ok).payload["values"]!!.jsonArray
            assertEquals(2, values.size)
            val byName = values.associate { it.jsonObject["name"]!!.jsonPrimitive.content to it.jsonObject }

            val rpmEntry = byName["RPM"]!!
            assertNull("did not expect RPM to error out: $rpmEntry", rpmEntry["error"])
            val rpmFormatted = rpmEntry["value"]?.jsonPrimitive?.content
            assertNotNull("expected a formatted RPM value", rpmFormatted)
            assertNotNull("expected an RPM unit", rpmEntry["unit"]?.jsonPrimitive?.content)
            // Same plausibility bound as
            // EcmSimProtocolIntegrationTest.pollRuntimeDataAcrossFiveFramesDecodesPlausibleVariables:
            // a Buell engine never spins anywhere near 20000 RPM.
            val rpmValue = rpmFormatted!!.toDouble()
            assertTrue("RPM=$rpmValue is not a plausible engine speed", rpmValue in 0.0..20000.0)

            val afvEntry = byName["AFV"]!!
            assertNull("did not expect AFV to error out: $afvEntry", afvEntry["error"])
            assertNotNull("expected a formatted AFV value", afvEntry["value"]?.jsonPrimitive?.content)
            assertNotNull("expected an AFV unit", afvEntry["unit"]?.jsonPrimitive?.content)
        } finally {
            ecm.disconnect()
        }
    }

    @Test
    fun readErrorCodes_returnsSimulatorCurrentCodes() {
        val ecm = newEcm()
        ecm.connect(newTcpTransport(), ECM.Protocol.STOCK)
        try {
            ecm.setupEEPROM()
            val result = runBlocking { newTools(ecm).readErrorCodes() }
            assertTrue("expected Ok, got $result", result is ToolResult.Ok)
            val payload = (result as ToolResult.Ok).payload
            val current = payload["current"]!!.jsonArray
            val stored = payload["stored"]!!.jsonArray
            // Structural validity only (don't hardcode specific codes):
            // every returned entry must carry code/description/type keys,
            // whatever the simulator's current fixture state produces.
            for (codes in listOf(current, stored)) {
                for (entry in codes) {
                    val obj = entry.jsonObject
                    assertTrue("expected a 'code' key: $obj", obj.containsKey("code"))
                    assertTrue("expected a 'description' key: $obj", obj.containsKey("description"))
                    assertTrue("expected a 'type' key: $obj", obj.containsKey("type"))
                }
            }
        } finally {
            ecm.disconnect()
        }
    }

    @Test
    fun getEepromParameter_afterFetch_returnsScaledEepromValue() {
        val variableProvider = JdbcVariableProvider(connection)
        val paramName = Constants.Variables.KBaro
        assertNotNull(
            "expected '$paramName' to resolve as a BUEIB EEPROM variable",
            variableProvider.getEEPROMVariable("BUEIB", paramName),
        )

        val ecm = newEcm()
        connectAndFetchAllPages(ecm)
        try {
            val result = runBlocking { newTools(ecm).getEepromParameter(paramName) }
            assertTrue("expected Ok, got $result", result is ToolResult.Ok)
            val payload = (result as ToolResult.Ok).payload
            assertEquals(paramName, payload["name"]?.jsonPrimitive?.content)
            val value = payload["value"]?.jsonPrimitive?.content
            assertNotNull("expected a scaled EEPROM value", value)
            assertFalse("expected a non-blank scaled value", value!!.isBlank())
            assertNotNull("expected a unit for '$paramName'", payload["unit"])
        } finally {
            ecm.disconnect()
        }
    }

    @Test
    fun getFuelMapRegion_returnsRequestedCellRange() {
        val ecm = newEcm()
        connectAndFetchAllPages(ecm)
        try {
            val result = runBlocking { newTools(ecm).getFuelMapRegion("front", 0..1, 0..1) }
            assertTrue("expected Ok, got $result", result is ToolResult.Ok)
            val payload = (result as ToolResult.Ok).payload
            assertEquals("front", payload["cylinder"]?.jsonPrimitive?.content)
            assertEquals(Constants.Variables.Tab_Fuel_Front, payload["variable"]?.jsonPrimitive?.content)

            val cells = payload["cells"]!!.jsonArray
            assertEquals("expected a 2-row region", 2, cells.size)
            for (row in cells) {
                val rowCells = row.jsonArray
                assertEquals("expected a 2-column region", 2, rowCells.size)
                for (cell in rowCells) {
                    // Raw pulse-width units, not scaled to microseconds
                    // (EcmTools.getFuelMapRegion's own contract) -- a
                    // single byte-backed table cell, so 0..255.
                    val raw = cell.jsonPrimitive.int
                    assertTrue("raw fuel-map cell $raw is outside the plausible pulse-width byte range", raw in 0..255)
                }
            }
        } finally {
            ecm.disconnect()
        }
    }

    @Test
    fun allSixTools_whenEcmNeverConnected_returnNotConnectedResult() {
        val ecm = newEcm()
        val tools = newTools(ecm)
        runBlocking {
            assertEquals(ToolResult.NotConnected, tools.getEcmInfo())
            assertEquals(ToolResult.NotConnected, tools.listLiveVariables())
            assertEquals(ToolResult.NotConnected, tools.readLiveData(listOf("RPM")))
            assertEquals(ToolResult.NotConnected, tools.readErrorCodes())
            assertEquals(ToolResult.NotConnected, tools.getEepromParameter(Constants.Variables.KBaro))
            assertEquals(ToolResult.NotConnected, tools.getFuelMapRegion("front", 0..1, 0..1))
        }
    }
}
