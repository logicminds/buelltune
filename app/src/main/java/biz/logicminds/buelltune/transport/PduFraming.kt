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
import java.text.ParseException

/**
 * The PDU wire-framing codec, extracted verbatim (KTD11) from the legacy
 * `ECM.sendPDU`/`ECM.receivePDU`/`ECM.read`. Every [EcmTransport]
 * implementation built on a blocking [InputStream]/[OutputStream] pair
 * (Bluetooth Classic, TCP) drives its `transact()` through [writeFrame]
 * and [readFrame] under its own `Mutex.withLock { }`; this object itself
 * holds no state and knows nothing about connections, so it cannot violate
 * KTD11's cleanup-outside-the-lock rule.
 *
 * Frame shape: a 6-byte header (`SOH`, sender, recipient, `len`, `EOH`,
 * `SOT`) followed by `len + 1` bytes - the payload, `EOT`, and the XOR
 * checksum - which [PDU]'s own constructor parses and validates.
 */
object PduFraming {
    /** SOH + sender + recipient + len + EOH + SOT. */
    const val HEADER_LENGTH = 6

    /**
     * Per-PDU response-time budget, read from the current `ECM.kt` before
     * this port: `private const val DEFAULT_TIMEOUT = 1000` (ms).
     */
    const val RESPONSE_TIMEOUT_MS = 1000

    /** Write [request]'s framed bytes. Verbatim from `ECM.sendPDU`'s `out.write(pdu.getBytes())`. */
    @Throws(IOException::class)
    fun writeFrame(output: OutputStream, request: PDU) {
        output.write(request.getBytes())
    }

    /**
     * Block (via a 10ms-interval availability poll, exactly as
     * `ECM.read`/`ECM.receivePDU` did) until one complete, checksum-valid
     * [PDU] has been read from [input] or [timeoutMs] elapses.
     *
     * On any failure - a short/timed-out read, an invalid header, or a
     * checksum mismatch - drains whatever is currently sitting in
     * [input]'s buffer before rethrowing, so a caller who retries on the
     * same stream resynchronizes to the next valid frame instead of
     * parsing stale/misaligned bytes (the post-failure resync logic
     * `ECM.receivePDU`'s `catch` block performed).
     *
     * [buffer] is scratch space, reused byte-for-byte from the previous
     * call - never read before being overwritten - so a caller whose own
     * `Mutex` already serializes every `readFrame` on a given connection
     * (every [EcmTransport] implementation does, inside `transact()`'s
     * `mutex.withLock { }`) can pass in one instance-owned array instead of
     * paying a fresh 256-byte allocation on every polled frame. Defaults to
     * a fresh allocation so callers with no such guarantee - e.g. a test
     * calling [readFrame] directly - are unaffected. Must be at least
     * [HEADER_LENGTH] + 256 bytes; a caller-supplied buffer is never
     * resized.
     */
    @Throws(IOException::class)
    fun readFrame(input: InputStream, timeoutMs: Int = RESPONSE_TIMEOUT_MS, buffer: ByteArray = ByteArray(256)): PDU {
        try {
            readFully(input, buffer, 0, HEADER_LENGTH, timeoutMs)
            if (buffer[0] != PDU.SOH && buffer[4] != PDU.EOH && buffer[5] != PDU.SOT) {
                throw IOException("Invalid Header received.")
            }
            val len = buffer[3].toInt() and 0xff
            readFully(input, buffer, HEADER_LENGTH, len + 1, timeoutMs)
            return try {
                PDU(buffer, len + 7)
            } catch (e: ParseException) {
                throw IOException("Unable to parse incoming PDU. " + e.localizedMessage)
            }
        } catch (ioe: IOException) {
            drain(input)
            throw ioe
        }
    }

    /**
     * Verbatim from `ECM.read(buffer, offset, len, timeoutMs)`: polls
     * [InputStream.available] every 10ms, draining whatever has arrived on
     * each wake-up, until [len] bytes have been read or [timeoutMs] is
     * exhausted.
     */
    @Throws(IOException::class)
    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int, len: Int, timeoutMs: Int): Int {
        if (offset + len >= buffer.size) {
            throw IOException("${offset + len}: Array index out of bounds.")
        }
        var timeout = timeoutMs
        var r = 0
        while (r < len && timeout > 0) {
            if (input.available() > 0) {
                do {
                    val toRead = minOf(len - r, input.available())
                    val i = try {
                        input.read(buffer, r + offset, toRead)
                    } catch (rte: RuntimeException) {
                        throw IOException("Runtime Exception while reading $toRead bytes at offset ${r + offset}")
                    }
                    if (i == -1) {
                        throw IOException("EOF while reading $toRead/$len bytes at offset ${r + offset}")
                    }
                    r += i
                } while (r < len && input.available() > 0)
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

    /** Verbatim from `ECM.receivePDU`'s `catch` block: discard whatever is currently buffered. */
    private fun drain(input: InputStream) {
        try {
            while (input.available() > 0) {
                input.read()
            }
        } catch (e: IOException) {
        }
    }
}
