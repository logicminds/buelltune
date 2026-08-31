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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import java.util.ArrayDeque

/**
 * Covers U7's framing-codec acceptance scenarios (R7, KTD11): chunked
 * reassembly, checksum rejection, and post-failure resync. All three are
 * deterministic, timing-free unit tests -- no socket, no thread, no
 * `Thread.sleep` on the happy path -- since every byte this test feeds
 * [PduFraming] is already "available" the instant it's queried.
 */
class PduFramingTest {

    @Test
    fun readFrameReassemblesAPduSplitAcrossThreeChunkBoundaries() {
        val pdu = PDU(PDU.DROID_ID, PDU.getECMID(), byteArrayOf(PDU.CMD_VERSION))
        val bytes = pdu.getBytes()
        // Three arbitrary, unequal chunk boundaries that don't line up with
        // the 6-byte header/body split.
        val cut1 = 2
        val cut2 = minOf(bytes.size - 1, 5)
        val chunks = listOf(
            bytes.copyOfRange(0, cut1),
            bytes.copyOfRange(cut1, cut2),
            bytes.copyOfRange(cut2, bytes.size),
        )
        val stream = ChunkedInputStream(chunks)

        val result = PduFraming.readFrame(stream)

        assertArrayEquals(bytes, result.getBytes())
    }

    @Test
    fun readFrameRejectsABadChecksum() {
        val pdu = PDU(PDU.DROID_ID, PDU.getECMID(), byteArrayOf(PDU.CMD_VERSION))
        val corrupted = pdu.getBytes().copyOf()
        corrupted[corrupted.size - 1] = (corrupted[corrupted.size - 1] + 1).toByte()
        val stream = ChunkedInputStream(listOf(corrupted))

        val thrown = assertThrows(IOException::class.java) { PduFraming.readFrame(stream) }
        assertTrueMessageMentionsParsing(thrown)
    }

    @Test
    fun readFrameAfterAPartialFrameDrainsAndResynchronizesToTheNextValidFrame() {
        val stream = FeedableInputStream()
        // A syntactically invalid 6-byte header (fails ECM.receivePDU's own
        // header-validity check immediately, no timeout wait needed).
        stream.feed(byteArrayOf(0, 0, 0, 0, 0, 0))
        // Garbage that had already arrived by the time of that failure --
        // must be drained, or it corrupts the next frame's header read.
        stream.feed(byteArrayOf(0x99.toByte(), 0x99.toByte(), 0x99.toByte()))

        assertThrows(IOException::class.java) { PduFraming.readFrame(stream) }

        // The next valid frame arrives on the wire only after the failed
        // one was drained.
        val valid = PDU(PDU.DROID_ID, PDU.getECMID(), byteArrayOf(PDU.CMD_VERSION))
        stream.feed(valid.getBytes())

        val result = PduFraming.readFrame(stream)
        assertArrayEquals(valid.getBytes(), result.getBytes())
    }

    private fun assertTrueMessageMentionsParsing(e: IOException) {
        org.junit.Assert.assertTrue(
            "expected a parse-failure message, got: ${e.message}",
            e.message?.contains("Unable to parse", ignoreCase = true) == true,
        )
    }
}

/**
 * Deterministic [InputStream] test double that reveals bytes one
 * pre-defined chunk at a time: [available] only ever reflects the current
 * chunk's unread remainder, forcing [PduFraming]'s `available()`-gated read
 * loop to issue one [read] call per chunk instead of slurping everything in
 * a single call -- exactly what "split across three chunk boundaries" means
 * on a real, fragmented network stream.
 */
internal class ChunkedInputStream(private val chunks: List<ByteArray>) : InputStream() {
    private var chunkIndex = 0
    private var pos = 0

    override fun available(): Int {
        if (chunkIndex >= chunks.size) return 0
        return chunks[chunkIndex].size - pos
    }

    override fun read(): Int {
        val one = ByteArray(1)
        val n = read(one, 0, 1)
        return if (n == -1) -1 else one[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (chunkIndex >= chunks.size) return -1
        val chunk = chunks[chunkIndex]
        val toRead = minOf(len, chunk.size - pos)
        System.arraycopy(chunk, pos, b, off, toRead)
        pos += toRead
        if (pos >= chunk.size) {
            chunkIndex++
            pos = 0
        }
        return toRead
    }
}

/**
 * Mutable [InputStream] test double that only ever exposes bytes explicitly
 * handed to it via [feed] -- models a real socket where "the next frame"
 * genuinely has not arrived yet until the test says so, letting the resync
 * test assert the drain only consumes what was pending *before* the
 * subsequent valid frame is fed.
 */
internal class FeedableInputStream : InputStream() {
    private val buffer = ArrayDeque<Byte>()

    fun feed(bytes: ByteArray) {
        bytes.forEach { buffer.addLast(it) }
    }

    override fun available(): Int = buffer.size

    override fun read(): Int = if (buffer.isEmpty()) -1 else (buffer.removeFirst().toInt() and 0xff)

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (buffer.isEmpty()) return -1
        var n = 0
        while (n < len && buffer.isNotEmpty()) {
            b[off + n] = buffer.removeFirst()
            n++
        }
        return n
    }
}
