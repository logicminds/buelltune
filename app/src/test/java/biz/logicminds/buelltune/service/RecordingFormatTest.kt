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

import biz.logicminds.buelltune.ECM
import biz.logicminds.buelltune.PDU
import biz.logicminds.buelltune.TestUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Collections

/**
 * Covers U8 step 3/R11/R14: [PollRecordLoop]'s recording byte layout must
 * match `EcmDroidService.startRecording`/`logPacket` exactly, byte for
 * byte, since `Bin2MslConverter` and every existing `.bin` log (plus the
 * committed `BUE2D_log.msl` reference U4 already verified) are calibrated
 * against that layout.
 *
 * **Verification method.** `git log --follow` on `EcmDroidService.java`
 * shows the logging logic (`startRecording`'s header write and
 * `logPacket`'s per-record write) has been byte-for-byte identical since
 * the original pre-fork commit (`9cfea5a` era) through the current
 * `biz.logicminds.buelltune` package - there is no historical drift to
 * extract a "different old version" from. The two tests below therefore
 * validate this loop's output two ways:
 *  1. [recordingBytesMatchGoldenDataOutputStreamEncoding] independently
 *     re-derives the expected bytes using the exact same JDK primitive the
 *     legacy code used (`DataOutputStream.write`/`writeInt` - a
 *     mechanical, not custom, implementation of "5-byte header, then
 *     big-endian timestamp `int` + raw frame bytes per record") for a
 *     fixed, arbitrary input sequence, and asserts [PollRecordLoop]'s sink
 *     output is byte-identical to it.
 *  2. [recordingBytesMatchRealCapturedBueibLog] cross-checks against
 *     `BUEIB_log.bin`, a real log file captured by the pre-refactor Java
 *     app on real hardware (also the fixture [TestUtils]/`Bin2MslConverter`
 *     already trust): it decodes that file's real 5-byte header and first
 *     107-byte-PDU record, re-parses the record as a real [PDU] to prove
 *     it is a well-formed, checksum-valid frame, and asserts that feeding
 *     the *exact same* id/timestamp/frame-bytes through [PollRecordLoop]
 *     reproduces that exact captured byte sequence.
 */
class RecordingFormatTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun recordingBytesMatchGoldenDataOutputStreamEncoding() {
        val ecmId = "BUEIB"
        val clock = FakeClock(startMillis = 1_000_000L)
        val frame1 = ackWithBytes(byteArrayOf(0x10, 0x20, 0x30)).getBytes()
        val frame2 = ackWithBytes(byteArrayOf(0x40, 0x50, 0x60, 0x70)).getBytes()

        // --- golden: literally the legacy startRecording()/logPacket() body, using the same java.io.DataOutputStream. ---
        val golden = ByteArrayOutputStream()
        val goldenLog = DataOutputStream(golden)
        goldenLog.write(ecmId.toByteArray(), 0, 5)
        val recordingStarted = clock.currentTimeMillis()
        clock.now = recordingStarted + 1234L
        goldenLog.writeInt(((clock.currentTimeMillis() - recordingStarted).toInt()) / 10)
        goldenLog.write(frame1)
        clock.now = recordingStarted + 5678L
        goldenLog.writeInt(((clock.currentTimeMillis() - recordingStarted).toInt()) / 10)
        goldenLog.write(frame2)
        goldenLog.flush()

        // --- actual: drive PollRecordLoop's recording path with the identical clock sequence. ---
        clock.now = recordingStarted
        val ecm = ECM(NoOpVariableProvider(), NoOpBitSetProvider(), FixedIdDefinitionsProvider(ecmId), null)
        val transport = FakeEcmTransport(mutableListOf(FakeOutcome.Reply(ackWithBytes("BUEIB310 12-11-03".toByteArray(Charsets.US_ASCII)))))
        ecm.connect(transport, ECM.Protocol.STOCK)
        ecm.setupEEPROM()

        val loop = PollRecordLoop(ecm, scope, clock = clock)
        val sink = CapturingSink()
        loop.startRecording(sink, 0)

        clock.now = recordingStarted + 1234L
        loop.record(frame1)
        clock.now = recordingStarted + 5678L
        loop.record(frame2)
        loop.stopRecording()

        assertArrayEquals(golden.toByteArray(), sink.toByteArray())
    }

    @Test
    fun recordingBytesMatchRealCapturedBueibLog() {
        val realLog = TestUtils.readBinaryLog()
        val header = realLog.copyOfRange(0, 5)
        assertEquals("BUEIB", String(header))

        val timestampBytes = realLog.copyOfRange(5, 9)
        val timestampTicks = ((timestampBytes[0].toInt() and 0xff) shl 24) or
            ((timestampBytes[1].toInt() and 0xff) shl 16) or
            ((timestampBytes[2].toInt() and 0xff) shl 8) or
            (timestampBytes[3].toInt() and 0xff)
        val pduBytes = realLog.copyOfRange(9, 9 + 107)

        // Prove the captured record body is a genuine, checksum-valid PDU frame
        // (SOH/EOH/SOT/EOT + XOR checksum) - i.e. the "full PDU" layout, not a
        // trimmed payload.
        val parsed = PDU(pduBytes, pduBytes.size)
        assertArrayEquals(pduBytes, parsed.getBytes())

        val clock = FakeClock(startMillis = 0L)
        val ecm = ECM(NoOpVariableProvider(), NoOpBitSetProvider(), FixedIdDefinitionsProvider("BUEIB"), null)
        val transport = FakeEcmTransport(mutableListOf(FakeOutcome.Reply(ackWithBytes("BUEIB310 12-11-03".toByteArray(Charsets.US_ASCII)))))
        ecm.connect(transport, ECM.Protocol.STOCK)
        ecm.setupEEPROM()

        val loop = PollRecordLoop(ecm, scope, clock = clock)
        val sink = CapturingSink()
        loop.startRecording(sink, 0)

        // The real file's timestamp field is `(millis-since-start)/10`; drive the
        // fake clock so recording this one frame reproduces that same tick value.
        clock.now = timestampTicks.toLong() * 10
        loop.record(pduBytes)
        loop.stopRecording()

        val expected = ByteArray(5 + 4 + 107)
        System.arraycopy(header, 0, expected, 0, 5)
        System.arraycopy(timestampBytes, 0, expected, 5, 4)
        System.arraycopy(pduBytes, 0, expected, 9, 107)

        assertArrayEquals(expected, sink.toByteArray())
        assertArrayEquals(realLog.copyOfRange(0, 5 + 4 + 107), sink.toByteArray())
    }
}

private class FakeClock(startMillis: Long) : Clock {
    var now: Long = startMillis
    override fun currentTimeMillis(): Long = now
}

private class CapturingSink : RecordingSink {
    private val buffer = ByteArrayOutputStream()
    private val chunks: MutableList<ByteArray> = Collections.synchronizedList(mutableListOf())

    override fun write(bytes: ByteArray) {
        chunks.add(bytes.copyOf())
        buffer.write(bytes)
    }

    override fun close() {
        buffer.flush()
    }

    fun toByteArray(): ByteArray = buffer.toByteArray()
}

