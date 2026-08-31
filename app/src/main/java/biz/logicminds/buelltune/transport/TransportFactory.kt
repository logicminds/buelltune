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

import android.bluetooth.BluetoothDevice
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Builds the right [EcmTransport] for a user-selected connection type,
 * replacing the four legacy `ECM.connect(...)` overloads (R7).
 * `MainActivity.ConnectTask` calls this instead of building a socket
 * itself, then hands the result to `ECM.connect(transport, protocol)`
 * (KTD4's one permitted legacy edit for this unit).
 *
 * Bluetooth Classic and TCP are the two connection types this unit builds.
 * BLE and USB-serial land in U13/U14 behind the same [EcmTransport]
 * contract; `MainActivity`'s BLE/USB-serial device pickers still exist, but
 * `ConnectTask` fails those two paths explicitly with a clear
 * "not yet available" [IOException] until those units land.
 */
object TransportFactory {
    /**
     * TCP/IP connection to `host:port`, matching the legacy
     * `ECM.connect(host, port, Protocol)`'s 5000ms connect timeout
     * ([TcpTransport.CONNECT_TIMEOUT_MS]).
     */
    @JvmStatic
    fun tcp(host: String, port: Int): EcmTransport = TcpTransport {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), TcpTransport.CONNECT_TIMEOUT_MS)
            socket
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }

    /**
     * Bluetooth Classic (SPP) connection to a paired [device], matching the
     * legacy `ECM.connect(BluetoothDevice, Protocol)`'s RFCOMM UUID
     * ([BluetoothClassicTransport.RFCOMM_UUID]).
     */
    @JvmStatic
    fun bluetoothClassic(device: BluetoothDevice): EcmTransport = BluetoothClassicTransport {
        val socket = device.createRfcommSocketToServiceRecord(BluetoothClassicTransport.RFCOMM_UUID)
        try {
            socket.connect()
            socket
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }
}
