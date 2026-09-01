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

import biz.logicminds.buelltune.PDU
import biz.logicminds.buelltune.transport.PduFraming
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

/**
 * A transparent, single-connection request/response relay sitting between
 * a [biz.logicminds.buelltune.transport.TcpTransport] under test and the
 * real `ecmsim` process at `targetHost:targetPort`. Frames both directions
 * with the real [PduFraming] codec (not a hand-rolled byte-position guess),
 * so it parses/reframes exactly like production.
 *
 * Two uses:
 *  - **Recording** ([requests]): every client request PDU is captured in
 *    order, so a test can assert the exact wire shape of a request (R16's
 *    EEPROM page-0 selective-write assertion) without teeing the socket
 *    itself.
 *  - **Corrupting one response** ([corruptNextResponse]): flips the
 *    trailing checksum byte of the *first* response relayed back to the
 *    client, verbatim (the response PDU's own bytes, not resynthesized) --
 *    the R16/KTD11 checksum-corruption scenario. `ecmsim` itself never
 *    naturally produces a bad checksum, so this is where the corruption is
 *    injected instead, as the plan's U10 approach step 5 anticipates.
 */
internal class PduRelay(
    private val targetHost: String,
    private val targetPort: Int,
    private val corruptNextResponse: Boolean = false,
) : AutoCloseable {
    private val serverSocket = ServerSocket(0)
    val localPort: Int get() = serverSocket.localPort

    /** Every request PDU seen from the client, in order. */
    val requests: MutableList<PDU> = Collections.synchronizedList(mutableListOf())

    @Volatile private var corrupted = false

    // Tracked so close() can force-close the live session, not just the
    // listening socket: PduFraming.readFully() detects a *graceful* peer
    // close (FIN, no more data) only by polling InputStream.available(),
    // which stays 0 after a FIN with nothing queued -- it does not notice
    // for up to its full response-timeout budget. A *forced* local close()
    // instead makes available()/read() throw immediately on the next 10ms
    // poll tick, so relay teardown (and therefore ecmsim noticing its
    // upstream connection died and looping back to accept()) stays fast
    // instead of stalling other tests for up to a second.
    @Volatile private var activeClient: Socket? = null
    @Volatile private var activeServer: Socket? = null

    private val thread = Thread {
        try {
            serverSocket.accept().use { client ->
                activeClient = client
                Socket().apply { connect(InetSocketAddress(targetHost, targetPort), 5000) }.use { server ->
                    activeServer = server
                    val clientIn = client.getInputStream()
                    val clientOut = client.getOutputStream()
                    val serverIn = server.getInputStream()
                    val serverOut = server.getOutputStream()
                    while (!client.isClosed && !server.isClosed) {
                        val request = try {
                            PduFraming.readFrame(clientIn)
                        } catch (e: IOException) {
                            break
                        }
                        requests.add(request)
                        PduFraming.writeFrame(serverOut, request)

                        val response = try {
                            PduFraming.readFrame(serverIn)
                        } catch (e: IOException) {
                            break
                        }
                        var bytes = response.getBytes()
                        if (corruptNextResponse && !corrupted) {
                            corrupted = true
                            bytes = bytes.copyOf()
                            bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0xFF).toByte()
                        }
                        clientOut.write(bytes)
                        clientOut.flush()
                    }
                }
            }
        } catch (e: IOException) {
            // Relay or upstream socket closed -- nothing left to relay.
        }
    }.apply { isDaemon = true }

    fun start() {
        thread.start()
    }

    /** Force-closes the listening socket and any live session, then waits (briefly) for the relay thread to finish tearing down before returning. */
    override fun close() {
        serverSocket.close()
        activeClient?.let { runCatching { it.close() } }
        activeServer?.let { runCatching { it.close() } }
        thread.join(2000)
    }
}
