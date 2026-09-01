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
package biz.logicminds.buelltune.service

import biz.logicminds.buelltune.BitSet
import biz.logicminds.buelltune.BitSetProvider
import biz.logicminds.buelltune.Constants
import biz.logicminds.buelltune.ECM
import biz.logicminds.buelltune.EcmDefinitionsProvider
import biz.logicminds.buelltune.EEPROM
import biz.logicminds.buelltune.PDU
import biz.logicminds.buelltune.Variable
import biz.logicminds.buelltune.VariableProvider
import biz.logicminds.buelltune.transport.ConnectionState
import biz.logicminds.buelltune.transport.EcmTransport
import biz.logicminds.buelltune.transport.FailureCause
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/**
 * Test doubles shared by [PollRecordLoop]'s JVM suite (R14). Follows the
 * same minimal-fake shape [biz.logicminds.buelltune.TestEcmProtocol]
 * already established for [ECM]'s own protocol tests, but drives [ECM]
 * through an in-memory [EcmTransport] fake instead of a loopback TCP
 * socket - [PollRecordLoop] only cares about the state-machine/exception
 * contract [EcmTransport] and [ECM] already guarantee (U7), not wire
 * timing, so a real socket would add nothing but latency here.
 */
internal fun ackWithBytes(data: ByteArray): PDU {
    val payload = ByteArray(1 + data.size)
    payload[0] = PDU.ACK
    System.arraycopy(data, 0, payload, 1, data.size)
    return PDU(PDU.getECMID(), PDU.DROID_ID, payload)
}

internal class NoOpVariableProvider : VariableProvider() {
    override fun getRtVariableNames(ecm: String): Collection<String> = emptyList()
    override fun getRtVariable(ecm: String, name: String): Variable? = null
    override fun getScalarRtVariableNames(ecm: String): Collection<String> = emptyList()
    override fun getBitfieldRtVariableNames(ecm: String): Collection<String> = emptyList()
    override fun getEEPROMVariable(ecm: String, name: String): Variable? = null
    override fun getName(varname: String): String? = null
    override fun getName(varname: String, bitnumber: Int): String? = null
    override fun getNearestEEPROMVariable(ecm: String, offset: Int): Variable? = null
}

internal class NoOpBitSetProvider : BitSetProvider() {
    override fun getBitSet(ecmId: String, name: String, source: Constants.DataSource): BitSet? = null
}

/** Always resolves [ecmId] to a fixed [EEPROM] shell carrying only an id - enough for [PollRecordLoop]'s header byte. */
internal class FixedIdDefinitionsProvider(private val id: String) : EcmDefinitionsProvider {
    override fun getEeprom(ecmId: String): EEPROM = EEPROM(id)
    override fun size2id(length: Int): List<String> = emptyList()
}

internal class NoOpDefinitionsProvider : EcmDefinitionsProvider {
    override fun getEeprom(ecmId: String): EEPROM? = null
    override fun size2id(length: Int): List<String> = emptyList()
}

internal fun newEcm(definitionsProvider: EcmDefinitionsProvider = NoOpDefinitionsProvider()): ECM =
    ECM(NoOpVariableProvider(), NoOpBitSetProvider(), definitionsProvider, null)

/** One scripted response to a [FakeEcmTransport.transact] call. */
internal sealed class FakeOutcome {
    data class Reply(val pdu: PDU) : FakeOutcome()

    /** Mirrors a real transport (KTD11): moves [FakeEcmTransport.state] to [ConnectionState.Failed] *before* throwing. */
    data class Fail(val exception: IOException) : FakeOutcome()
}

/**
 * Minimal [EcmTransport] fake: [connect] moves straight to
 * [ConnectionState.Connected], and [transact] plays back [script] in
 * order - one entry per call, regardless of which PDU command was sent
 * (tests order the script to match the request sequence [ECM] is known to
 * issue: one version request from [ECM.setupEEPROM], then one runtime-data
 * request per [ECM.readRTData] call).
 */
internal class FakeEcmTransport(private val script: MutableList<FakeOutcome>) : EcmTransport {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    override suspend fun connect() {
        _state.value = ConnectionState.Connected
    }

    override suspend fun disconnect() {
        _state.value = ConnectionState.Disconnected
    }

    override suspend fun transact(request: PDU): PDU {
        // Repeats the final scripted outcome instead of exhausting the list:
        // a test that stops the loop based on an observed record/byte count
        // can otherwise race one extra poll cycle landing before the stop
        // takes effect (MINIMUM_INTERVAL_MS is real wall-clock time).
        val outcome = if (script.size > 1) script.removeAt(0) else script.first()
        return when (outcome) {
            is FakeOutcome.Reply -> outcome.pdu
            is FakeOutcome.Fail -> {
                _state.value = ConnectionState.Failed(FailureCause.Io(outcome.exception))
                throw outcome.exception
            }
        }
    }
}
