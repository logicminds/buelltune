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
import kotlinx.coroutines.flow.StateFlow

/**
 * Unifies Bluetooth Classic (SPP), BLE, USB-serial, and TCP/IP behind one
 * coroutine connection interface (R7, KD3, KTD11), replacing the four
 * `ECM.connect()` overloads that used to build a raw `BluetoothSocket` /
 * `SerialSocket` / `UsbSerialPort` / `java.net.Socket` inline.
 *
 * The raw byte stream is deliberately **not** part of this contract - it
 * stays internal to each implementation. [transact] is the only way bytes
 * cross the boundary, and it owns framing, the response-time budget, and
 * the post-failure resync drain (all ported verbatim from
 * `ECM.sendPDU`/`ECM.receivePDU`/`ECM.read`, see [biz.logicminds.buelltune.transport.PduFraming]).
 * One outstanding PDU at a time is a hard invariant: it is what stops a
 * `BurnTask` page write from interleaving with a poll loop's `readRTData`
 * and landing a mis-paired response in a page buffer that later gets
 * burned to a real ECM.
 *
 * KTD11's non-reentrancy rule governs every implementation of this
 * interface: [transact]'s internal `Mutex.withLock { }` covers only the
 * write, the framed read, and the resync drain. The moment an I/O failure
 * occurs, it propagates *out* of the locked scope; the transition to
 * [ConnectionState.Failed] and the release of any transport resources
 * happen after `withLock` returns/throws - never from a nested call made
 * while still holding the lock. `kotlinx.coroutines.sync.Mutex` is not
 * reentrant like the `synchronized` blocks it replaces; violating this
 * rule hangs forever on the very first real link drop.
 */
interface EcmTransport {
    /**
     * The current connection state, observable without polling. See
     * [ConnectionState] for the full transition contract.
     */
    val state: StateFlow<ConnectionState>

    /**
     * Establish the connection using this transport's typed, already
     * target-bound connection factory (KTD11 step 4 - e.g. a
     * `suspend () -> BluetoothSocket` for Bluetooth Classic, a
     * `suspend () -> java.net.Socket` for TCP). Transitions
     * [state] through [ConnectionState.Connecting] to either
     * [ConnectionState.Connected] on success, or [ConnectionState.Failed]
     * on failure - and rethrows the triggering exception in the failure
     * case, so blocking callers bridging in via `runBlocking` (KTD11) still
     * observe a thrown exception the way the legacy `ECM.connect()`
     * overloads did.
     */
    @Throws(Exception::class)
    suspend fun connect()

    /**
     * Send [request] and return the ECM's response, serialized against
     * every other concurrent caller of this method (the one-outstanding-PDU
     * invariant, R7). On an I/O failure, transitions [state] to
     * [ConnectionState.Failed] with a [FailureCause.Io] cause and rethrows,
     * per KTD11's cleanup-outside-the-lock rule.
     */
    @Throws(Exception::class)
    suspend fun transact(request: PDU): PDU

    /**
     * User-initiated disconnect. Always ends in [ConnectionState.Disconnected]
     * - never [ConnectionState.Failed] - which is the distinction the UI
     * needs to avoid showing a spurious error for an intentional
     * disconnect. Safe to call from any state, including when already
     * disconnected.
     */
    suspend fun disconnect()
}
