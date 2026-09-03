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

import biz.logicminds.buelltune.chat.EcmTools
import biz.logicminds.buelltune.chat.ToolResult
import biz.logicminds.buelltune.integration.AssetDatabase
import biz.logicminds.buelltune.integration.JdbcBitSetProvider
import biz.logicminds.buelltune.integration.JdbcEcmDefinitionsProvider
import biz.logicminds.buelltune.integration.JdbcVariableProvider
import biz.logicminds.buelltune.transport.ConnectionState
import biz.logicminds.buelltune.transport.EcmTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection

/**
 * Pure-JVM proof of [EcmTools]' scaling/not-connected/eeprom-not-read
 * behavior (R23, plan U3) - no live simulator, no real network
 * [EcmTransport] involved anywhere in this class.
 *
 * "Connected" is faked with [FakeEcmTransport], a minimal [EcmTransport]
 * that flips [ECM.isConnected] true via [ECM.connect]'s ordinary handshake
 * path without ever touching a socket. Runtime data comes from a real
 * captured PDU response frame (`RT_BUEIB242.bin` via
 * [TestUtils.readRTData]), parsed once through [PDU]'s byte-array
 * ("parse") constructor and replayed verbatim from every
 * [FakeEcmTransport.transact] call - matching how [ECM.readRTData] stores
 * the *full* raw frame (not just the payload) into [ECM.runtimeData] in
 * production.
 */
class TestEcmTools {

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

    private fun newTools(ecm: ECM): EcmTools = EcmTools(
        ecm,
        JdbcVariableProvider(connection),
        JdbcEcmDefinitionsProvider(connection),
    )

    /** The BUEIB EEPROM skeleton, filled with real fixture bytes. [read] controls [EEPROM.isEepromRead]. */
    private fun bueibEeprom(read: Boolean): EEPROM {
        val eeprom = JdbcEcmDefinitionsProvider(connection).getEeprom("BUEIB")!!
        eeprom.setBytes(TestUtils.readEEPROM())
        eeprom.setEepromRead(read)
        return eeprom
    }

    /** Connects [ecm] against a [FakeEcmTransport] so [ECM.isConnected] is true - no socket involved. */
    private fun connectFake(ecm: ECM) {
        val bytes = TestUtils.readRTData()
        ecm.connect(FakeEcmTransport(PDU(bytes, bytes.size)), ECM.Protocol.STOCK)
    }

    private class FakeEcmTransport(private val rtResponse: PDU) : EcmTransport {
        private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val state: StateFlow<ConnectionState> = _state.asStateFlow()
        override suspend fun connect() { _state.value = ConnectionState.Connected }
        override suspend fun transact(request: PDU): PDU = rtResponse
        override suspend fun disconnect() { _state.value = ConnectionState.Disconnected }
    }

    @Test
    fun readLiveData_knownVariable_returnsScaledValueWithUnit() = runBlocking {
        val ecm = newEcm()
        connectFake(ecm)
        ecm.setEEPROM(bueibEeprom(read = true))
        val tools = newTools(ecm)

        val result = tools.readLiveData(listOf("RPM"))

        val ok = result as? ToolResult.Ok ?: error("expected Ok, got $result")
        val entry = ok.payload.getValue("values").jsonArray[0].jsonObject
        assertEquals("RPM", entry.getValue("name").jsonPrimitive.contentOrNull)
        assertNotNull("expected a formatted value", entry["value"]?.jsonPrimitive?.contentOrNull)
        assertNotNull("expected a unit", entry["unit"]?.jsonPrimitive?.contentOrNull)
        assertNull("did not expect a per-item error", entry["error"])
    }

    @Test
    fun readLiveData_unknownVariable_returnsPerItemErrorNotException() = runBlocking {
        val ecm = newEcm()
        connectFake(ecm)
        ecm.setEEPROM(bueibEeprom(read = true))
        val tools = newTools(ecm)

        // Must not throw: an unresolvable name becomes a per-item error
        // entry inside a normal Ok result, not an aborted/exceptional call.
        val result = tools.readLiveData(listOf("NotARealVariable"))

        val ok = result as? ToolResult.Ok ?: error("expected Ok (per-item error), got $result")
        val entry = ok.payload.getValue("values").jsonArray[0].jsonObject
        assertEquals("NotARealVariable", entry.getValue("name").jsonPrimitive.contentOrNull)
        assertNotNull("expected a per-item error message", entry["error"]?.jsonPrimitive?.contentOrNull)
        assertNull("unresolved variable must not carry a value", entry["value"])
    }

    @Test
    fun readErrorCodes_matchesFixtureCurrentAndStoredCodes() = runBlocking {
        val ecm = newEcm()
        connectFake(ecm)
        ecm.setEEPROM(bueibEeprom(read = true))
        // Pre-populate runtimeData directly with the same raw frame bytes
        // ECM.readRTData() would have stored, so ECM.getErrors() (called
        // internally by EcmTools.readErrorCodes) takes its "already have
        // data" branch instead of invoking transact() itself.
        ecm.runtimeData = TestUtils.readRTData()
        val tools = newTools(ecm)

        val result = tools.readErrorCodes()

        val ok = result as? ToolResult.Ok ?: error("expected Ok, got $result")
        assertNotNull("expected a current-codes array", ok.payload["current"]?.jsonArray)
        assertNotNull("expected a stored-codes array", ok.payload["stored"]?.jsonArray)
    }

    @Test
    fun getEepromParameter_beforeFetch_returnsNotYetReadResult() = runBlocking {
        val ecm = newEcm()
        connectFake(ecm)
        ecm.setEEPROM(bueibEeprom(read = false))
        val tools = newTools(ecm)

        val result = tools.getEepromParameter(Constants.Variables.KTPS0)

        assertTrue("expected EepromNotRead, got $result", result is ToolResult.EepromNotRead)
    }

    @Test
    fun getEepromParameter_afterFetch_returnsScaledValue() = runBlocking {
        val ecm = newEcm()
        connectFake(ecm)
        ecm.setEEPROM(bueibEeprom(read = true))
        val tools = newTools(ecm)

        val result = tools.getEepromParameter(Constants.Variables.KTPS0)

        val ok = result as? ToolResult.Ok ?: error("expected Ok, got $result")
        assertEquals(Constants.Variables.KTPS0, ok.payload.getValue("name").jsonPrimitive.contentOrNull)
        assertNotNull("expected a formatted value", ok.payload["value"]?.jsonPrimitive?.contentOrNull)
        assertNotNull("expected a unit", ok.payload["unit"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun allSixTools_whenNotConnected_returnStructuredNotConnectedResult() = runBlocking {
        val ecm = newEcm() // never connect()-ed
        val tools = newTools(ecm)

        assertTrue(tools.getEcmInfo() is ToolResult.NotConnected)
        assertTrue(tools.listLiveVariables() is ToolResult.NotConnected)
        assertTrue(tools.readLiveData(listOf("AFV")) is ToolResult.NotConnected)
        assertTrue(tools.readErrorCodes() is ToolResult.NotConnected)
        assertTrue(tools.getEepromParameter("AFV") is ToolResult.NotConnected)
        assertTrue(tools.getFuelMapRegion("front", 0..1, 0..1) is ToolResult.NotConnected)
    }
}
