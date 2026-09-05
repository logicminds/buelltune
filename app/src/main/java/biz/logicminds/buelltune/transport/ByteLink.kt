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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The suspend duplex byte contract every [EcmTransport] hands to
 * [PduFraming] (R1, KTD1), replacing the `java.io.InputStream`/
 * `OutputStream` pair the framing codec used to take.
 *
 * This is the single seam that made the engine JVM-bound: the old shape
 * required a blocking stream, and the only way to satisfy it from a
 * callback-driven radio was to fake one (`PolledByteQueueInputStream`,
 * deleted in R2) and poll it with `available()`-then-`Thread.sleep(10)`.
 * A suspend contract over a channel needs neither: a stalled read is
 * bounded by `withTimeout` rather than by a manual millisecond budget,
 * and a chunk that arrives before anyone asks for it is buffered by the
 * channel instead of by a hand-rolled `ArrayDeque` under `synchronized`.
 *
 * Implementations are **not** required to be thread-safe on their own:
 * every transport already serializes `transact()` through its own
 * `Mutex.withLock { }` (the one-outstanding-PDU invariant, KTD11), so a
 * link is only ever driven by one coroutine at a time.
 */
interface ByteLink {
    /**
     * Bytes as the peer delivers them, one element per radio/socket
     * delivery. Chunk boundaries carry no protocol meaning - a single PDU
     * may arrive split across several elements, and [PduFraming] is what
     * reassembles them into frames.
     *
     * Closed with a cause when the link fails, so a reader suspended on
     * [ReceiveChannel.receive] fails immediately with that cause rather
     * than idling out its full response budget.
     */
    val incoming: ReceiveChannel<ByteArray>

    /** Send [bytes] to the peer, suspending until they are handed off. */
    suspend fun write(bytes: ByteArray)

    /** Release the link. Safe to call more than once. */
    suspend fun close()
}

/**
 * [ByteLink] over a real blocking [InputStream]/[OutputStream] pair, used
 * by the two transports that genuinely have one - [TcpTransport] and
 * [BluetoothClassicTransport] (KTD2).
 *
 * A pump coroutine on [ioDispatcher] performs the blocking reads and
 * forwards each chunk to [incoming], so the blocking call is confined to
 * one place instead of being the framing codec's problem. Behaviour is
 * otherwise unchanged from the streams it wraps.
 */
internal class StreamByteLink(
    private val input: InputStream,
    private val output: OutputStream,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ByteLink {

    private val channel = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<ByteArray> = channel

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val pump: Job = scope.launch {
        val buf = ByteArray(READ_CHUNK)
        try {
            while (true) {
                val n = input.read(buf)
                if (n == -1) {
                    channel.close(IOException("EOF on link."))
                    return@launch
                }
                if (n > 0) channel.send(buf.copyOf(n))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A read on a socket closed by disconnect() lands here; the
            // cause travels to whoever is waiting on incoming.
            channel.close(e)
        }
    }

    override suspend fun write(bytes: ByteArray) {
        withContext(ioDispatcher) {
            output.write(bytes)
            output.flush()
        }
    }

    override suspend fun close() {
        pump.cancel()
        channel.close()
    }

    private companion object {
        /**
         * One read's scratch size. Chunk boundaries are not protocol
         * boundaries, so this only bounds how much a single read can
         * return, never how a frame is assembled.
         */
        const val READ_CHUNK = 256
    }
}

/**
 * [ByteLink] for callback-driven transports - [BleTransport] and
 * [UsbSerialTransport] (KTD2). Their radios push chunks at us; there is
 * no stream to read, which is exactly why the deleted
 * `PolledByteQueueInputStream` existed.
 *
 * [offer] is called from the transport's own callback thread; [fail]
 * closes [incoming] with the reported cause so a suspended reader
 * observes the failure immediately. [sink] performs the actual write.
 */
internal class ChannelByteLink(
    private val sink: suspend (ByteArray) -> Unit,
    private val onClose: suspend () -> Unit = {},
) : ByteLink {

    private val channel = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<ByteArray> = channel

    /** Feed a chunk delivered by the transport's callback. */
    fun offer(data: ByteArray) {
        channel.trySend(data)
    }

    /** Report a link failure; [incoming] closes carrying [cause]. */
    fun fail(cause: Throwable) {
        channel.close(cause)
    }

    override suspend fun write(bytes: ByteArray) = sink(bytes)

    override suspend fun close() {
        channel.close()
        onClose()
    }
}
