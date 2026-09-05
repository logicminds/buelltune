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
import biz.logicminds.buelltune.PduParseException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * The PDU wire-framing codec (KTD11), driven by every [EcmTransport]
 * implementation from inside its own `Mutex.withLock { }`. This object
 * holds no state and knows nothing about connections, so it cannot
 * violate KTD11's cleanup-outside-the-lock rule.
 *
 * Frame shape: a 6-byte header (`SOH`, sender, recipient, `len`, `EOH`,
 * `SOT`) followed by `len + 1` bytes - the payload, `EOT`, and the XOR
 * checksum - which [PDU]'s own constructor parses and validates.
 *
 * Reads suspend on a [ByteLink] under `withTimeout` (R1, R3). The
 * previous implementation polled `InputStream.available()` every 10ms and
 * decremented a manual budget; that woke the thread 100 times a second
 * per outstanding PDU on a loop whose own poll interval is 50-5000ms.
 */
object PduFraming {
    /** SOH + sender + recipient + len + EOH + SOT. */
    const val HEADER_LENGTH = 6

    /**
     * Default per-PDU response budget, inherited from the legacy
     * `ECM.kt`'s `private const val DEFAULT_TIMEOUT = 1000` (ms).
     *
     * Transports supply their own value (R4, KD8): this number was tuned
     * against 9600-baud SPP, and a BLE round-trip crosses at least one
     * connection interval, so it is a default rather than a constant.
     */
    const val DEFAULT_RESPONSE_TIMEOUT_MS = 1000L

    /** Write [request]'s framed bytes to [link]. */
    @Throws(IOException::class)
    suspend fun writeFrame(link: ByteLink, request: PDU) {
        link.write(request.getBytes())
    }

    /**
     * Suspend until one complete, checksum-valid [PDU] has been read from
     * [link], or [timeoutMs] elapses.
     *
     * On any failure - a timed-out read, an invalid header, or a checksum
     * mismatch - drains whatever has already been delivered before
     * rethrowing, so a caller who retries on the same link resynchronizes
     * to the next valid frame instead of parsing misaligned bytes (the
     * post-failure resync `ECM.receivePDU`'s `catch` block performed).
     *
     * [buffer] is scratch space, reused byte-for-byte across calls - never
     * read before being overwritten - so a caller whose `Mutex` already
     * serializes every `readFrame` on a given link (every [EcmTransport]
     * does, inside `transact()`) can pass one instance-owned array instead
     * of allocating 256 bytes per polled frame. Must hold
     * [HEADER_LENGTH] + 256 bytes; a caller-supplied buffer is never
     * resized.
     */
    @Throws(IOException::class)
    suspend fun readFrame(
        link: ByteLink,
        timeoutMs: Long = DEFAULT_RESPONSE_TIMEOUT_MS,
        buffer: ByteArray = ByteArray(256),
    ): PDU {
        val carry = Carry()
        try {
            readFully(link, carry, buffer, 0, HEADER_LENGTH, timeoutMs)
            if (buffer[0] != PDU.SOH && buffer[4] != PDU.EOH && buffer[5] != PDU.SOT) {
                throw IOException("Invalid Header received.")
            }
            val len = buffer[3].toInt() and 0xff
            readFully(link, carry, buffer, HEADER_LENGTH, len + 1, timeoutMs)
            return try {
                PDU(buffer, len + 7)
            } catch (e: PduParseException) {
                throw IOException("Unable to parse incoming PDU. " + e.localizedMessage)
            }
        } catch (e: ClosedReceiveChannelException) {
            throw IOException("Link closed while reading PDU.", e)
        } catch (ioe: IOException) {
            drain(link)
            throw ioe
        }
    }

    /**
     * Accumulate exactly [len] bytes into [buffer] at [offset], suspending
     * on [ByteLink.incoming] until they arrive.
     *
     * [timeoutMs] is an **idle** budget, bounding the wait for the next
     * delivery rather than the whole read. This is deliberate and matches
     * the semantics of the `available()`-polling loop this replaced, whose
     * `timeout -= 10` sat in the *else* branch - time spent actually
     * receiving bytes consumed no budget, and the header and payload each
     * got a fresh one. A single wall-clock budget spanning both would be
     * strictly tighter than the old code: at 9600 baud a long frame costs
     * real transmission time, and an ECM answering slowly but healthily
     * would fail a write mid-page, since `ECM.writeEEPromPage` has no
     * retry.
     *
     * Bytes past the requested length stay in [carry] for the next call -
     * one delivery routinely spans the header/payload boundary.
     */
    private suspend fun readFully(
        link: ByteLink,
        carry: Carry,
        buffer: ByteArray,
        offset: Int,
        len: Int,
        timeoutMs: Long,
    ) {
        if (offset + len >= buffer.size) {
            throw IOException("${offset + len}: Array index out of bounds.")
        }
        var written = carry.drainInto(buffer, offset, len)
        while (written < len) {
            val chunk = try {
                withTimeout(timeoutMs) { link.incoming.receive() }
            } catch (e: TimeoutCancellationException) {
                throw IOException("Timeout reading $written from $len bytes at offset $offset.")
            }
            val take = minOf(len - written, chunk.size)
            chunk.copyInto(buffer, offset + written, 0, take)
            written += take
            if (take < chunk.size) carry.push(chunk, take)
        }
    }

    /** Discard whatever has already been delivered, per the legacy resync. */
    private fun drain(link: ByteLink) {
        while (link.incoming.tryReceive().isSuccess) {
            // discard
        }
    }

    /**
     * Leftover bytes from a delivery that overran the requested length.
     * Scoped to one [readFrame] call: a frame's trailing bytes belong to
     * that frame's reader, and anything still held when the read ends is
     * discarded with the rest of the resync.
     */
    private class Carry {
        private var bytes: ByteArray? = null
        private var pos = 0

        fun push(chunk: ByteArray, from: Int) {
            bytes = chunk
            pos = from
        }

        fun drainInto(buffer: ByteArray, offset: Int, len: Int): Int {
            val held = bytes ?: return 0
            val take = minOf(len, held.size - pos)
            held.copyInto(buffer, offset, pos, pos + take)
            pos += take
            if (pos >= held.size) {
                bytes = null
                pos = 0
            }
            return take
        }
    }
}
