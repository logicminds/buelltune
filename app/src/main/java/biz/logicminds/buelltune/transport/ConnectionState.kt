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

/**
 * Observable connection lifecycle of an [EcmTransport] (R7, R8, R11, F1,
 * AE1). This is the single source of truth callers use to tell "connected",
 * "connecting", "user disconnected", and "lost the link" apart, replacing
 * the flat `connected: Boolean` the legacy transports used.
 *
 * ```
 * Disconnected --connect()--> Connecting --handshake ok--> Connected
 * Connecting --IOException/SecurityException/timeout--> Failed
 * Connected --disconnect() (user-initiated)--> Disconnected
 * Connected --read/write IOException (link loss)--> Failed
 * ```
 *
 * Entering [Failed] from [Connected] is the `f3337a1`/#21 path: the service
 * layer (U8) reacts by stopping the poll loop, flushing and closing any
 * in-progress recording, and flipping the UI to disconnected.
 */
sealed class ConnectionState {
    /** No connection attempt in progress; either never connected, or a prior connection ended cleanly. */
    object Disconnected : ConnectionState()

    /** A [EcmTransport.connect] call is in flight. */
    object Connecting : ConnectionState()

    /** The handshake succeeded; [EcmTransport.transact] may be called. */
    object Connected : ConnectionState()

    /**
     * The most recent connect attempt or an in-flight [EcmTransport.transact]
     * failed. [cause] is what lets a caller distinguish a permission denial
     * (#8) from a plain I/O drop (#21) without sniffing exception types
     * itself.
     */
    data class Failed(val cause: FailureCause) : ConnectionState()
}

/**
 * Why an [EcmTransport] transitioned to [ConnectionState.Failed]. Carries
 * the triggering exception for logging/diagnostics.
 */
sealed class FailureCause {
    /**
     * A runtime permission required by the transport (e.g. Android 12+'s
     * `BLUETOOTH_CONNECT`) was not granted. This is the #8 bug's exact
     * signature - it arrives as a [SecurityException], not an [java.io.IOException].
     */
    data class PermissionDenied(val exception: Throwable) : FailureCause()

    /** A read, write, or connect attempt failed with an I/O error. */
    data class Io(val exception: Throwable) : FailureCause()

    /** A connect attempt did not complete within the transport's connect timeout. */
    data class Timeout(val exception: Throwable? = null) : FailureCause()
}
