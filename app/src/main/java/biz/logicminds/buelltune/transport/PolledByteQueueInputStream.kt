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

import java.io.IOException
import java.io.InputStream

/**
 * Bridges a listener callback's pushed byte chunks onto the synchronous,
 * poll-based [InputStream] shape [PduFraming.readFrame] drives - without a
 * [java.io.PipedInputStream]. Shared by [BleTransport] and
 * [UsbSerialTransport] (KTD3, KD3): both transports' `SerialListener`/
 * `SerialInputOutputManager.Listener` callbacks feed [offer] from their own
 * callback thread; [PduFraming.readFully]'s own `available()`-then-sleep
 * polling loop is what bounds a stalled read, by its normal response-time
 * budget - [read]/[available] here never block, they only ever look at
 * bytes already queued. This is the deletion both transports' unit is
 * named for: a piped stream's `read()` blocks until bytes arrive or the
 * writer end is explicitly closed, which is exactly what stalled forever
 * on a dropped BLE link until `f3337a1` added a manual `out.close()` to
 * unstick it.
 *
 * Backed by an [ArrayDeque] rather than array-concatenation: [offer]
 * appends in amortized O(chunk size) - never recopying bytes already
 * queued - unlike a naive `byteArray += chunk`, which recopies the entire
 * accumulated backlog on every single push. That matters here because a
 * multi-chunk streaming burst (several small `onNewData`/`onSerialRead`
 * callbacks landing before the poll loop next drains the queue) hits
 * exactly that append path repeatedly.
 *
 * [fail] lets a reported I/O error short-circuit the poll budget: once the
 * queue drains, the next [available] (and [read]) call throws the
 * recorded [IOException] immediately instead of idling out the full
 * [PduFraming.RESPONSE_TIMEOUT_MS] timeout.
 */
internal class PolledByteQueueInputStream : InputStream() {
    private val lock = Any()
    private val pending = ArrayDeque<Byte>()

    @Volatile
    private var error: IOException? = null

    fun offer(data: ByteArray) {
        if (data.isEmpty()) return
        synchronized(lock) {
            for (b in data) pending.addLast(b)
        }
    }

    fun fail(e: Exception) {
        error = e as? IOException ?: IOException(e.message, e)
    }

    override fun available(): Int = synchronized(lock) {
        if (pending.isEmpty()) error?.let { throw it }
        pending.size
    }

    override fun read(): Int = synchronized(lock) {
        if (pending.isEmpty()) {
            error?.let { throw it }
            return -1
        }
        pending.removeFirst().toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int = synchronized(lock) {
        if (pending.isEmpty()) {
            error?.let { throw it }
            return -1
        }
        var n = 0
        while (n < len && pending.isNotEmpty()) {
            b[off + n] = pending.removeFirst()
            n++
        }
        n
    }
}
