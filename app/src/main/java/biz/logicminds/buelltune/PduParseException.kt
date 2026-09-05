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
package biz.logicminds.buelltune

/**
 * A malformed or checksum-invalid [PDU] (R5, KTD3), replacing the
 * `java.text.ParseException` the protocol core previously threw.
 *
 * `ParseException` is a JVM-only text-parsing type: it belongs to
 * `java.text`, carries an error *offset* meant for locating a fault in a
 * parsed string, and has no Kotlin-common equivalent. Owning the type
 * keeps the protocol core portable and stops a wire-framing failure from
 * being reported as a text-formatting one.
 *
 * [errorOffset] preserves the byte position the old call sites passed, so
 * the diagnostic value of the original throws is not lost.
 */
class PduParseException(
    message: String,
    val errorOffset: Int = 0,
) : Exception(message)
