/*
 Bin2Msl Converter, Copyright (C) 2013 by Gunter Baumann

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
package biz.logicminds.buelltune.util

import android.util.Log

import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.ArrayList
import java.util.Locale
import java.util.Observable
import java.util.regex.Pattern

/**
 * BIN to MSL (MegalogViewer) Logfile converter.
 *
 * The original Java version extended the deprecated `java.util.Observable`
 * to report progress; no caller used the observer channel for anything but
 * progress messages, but `LogFragment.StopTask` (a legacy Java file outside
 * this unit's scope) still does `converter.addObserver(this)` and implements
 * `java.util.Observer`, so that inheritance is kept here to avoid a forced
 * edit to that file. The optional [progressCallback] constructor parameter
 * is an additive, JVM-testable alternative to registering an `Observer`
 * (`java.util.Observable`/`Observer` are plain `java.util` types with zero
 * Android dependency either way, so keeping the inheritance does not affect
 * JVM-testability). `android.util.Log` is the only real Android coupling
 * this class has; it is routed through the settable [logSink] seam below so
 * tests can run without touching the android.jar stub classpath at all.
 */
class Bin2MslConverter @JvmOverloads constructor(
	private val progressCallback: ((Int) -> Unit)? = null
) : Observable() {

	private var cancelled = false

	/**
	 * Convert a logfile from binary format into msl
	 *
	 * @param bin an input stream for the binary log
	 * @param msl an output stream receiving the converted data
	 * @throws IOException if an error occurs during conversion
	 * @since EcmDroid v0.9
	 */
	@Throws(IOException::class)
	fun convert(bin: InputStream, msl: OutputStream) {

		val buffer5 = ByteArray(5)
		var rtBuffer: IntArray? = null

		var numRecord = 0
		var numDiscard = 0
		var offsAFV = 0
		var offsAFV1 = 0
		var offsEGOCorr = 0
		var offsEGOCorr1 = 0
		var offsFlags1 = 0
		var offsFlags2 = 0
		var offsAccel = 0
		var offsWUE = 0
		val rc: Int
		var category = 0
		var rtLen = 0
		val loc = Locale("en")

		val ecmType: String
		val tabFileName: String

		var offsetsTable: BufferedReader? = null
		var binFile: DataInputStream? = null
		var mslFile: PrintWriter? = null

		val p = Pattern.compile("\t")

		val alCategory = ArrayList<Int>()
		val alSecret = ArrayList<String>()
		val alSize = ArrayList<Int>()
		val alOffset = ArrayList<Int>()
		val alScale = ArrayList<Float>()
		val alTranslate = ArrayList<Float>()
		val alFormat = ArrayList<String>()
		val alExport = ArrayList<String>()

		try {
			binFile = DataInputStream(BufferedInputStream(bin))
			mslFile = PrintWriter(msl, false)

			try {
				rc = binFile.read(buffer5)
				logD("read $rc bytes from binFile")
			} catch (e: EOFException) {
				logD("unexpected EOF")
				binFile.close()
				throw e
			}

			ecmType = String(buffer5)

			if (ecmType.startsWith("KA") || ecmType == "BUEKA") {
				logD("Type: BUEKA")
				category = 1
				rtLen = 99
				rtBuffer = IntArray(rtLen)
			} else if (ecmType.startsWith("JA") || ecmType == "BUEJA") {
				logD("Type: BUEJA")
				category = 1
				rtLen = 99
				rtBuffer = IntArray(rtLen)
			} else if (ecmType.startsWith("CB") || ecmType == "BUECB") {
				logD("Type: BUECB")
				category = 2
				rtLen = 103
				rtBuffer = IntArray(rtLen)
			} else if (ecmType.startsWith("GB") || ecmType == "BUEGB") {
				logD("Type: BUEGB")
				category = 2
				rtLen = 107
				rtBuffer = IntArray(rtLen)
			} else if (ecmType.startsWith("IB") || ecmType.startsWith("IC") || ecmType == "BUEIB" || ecmType == "B2RIB" || ecmType == "BUEIC") {
				logD("Type: BUEIB")
				category = 2
				rtLen = 107
				rtBuffer = IntArray(rtLen)
			} else if (ecmType.startsWith("OD") || ecmType == "BUEOD") {
				logD("Type: BUEYD")
				category = 3
				rtLen = 135
				rtBuffer = IntArray(rtLen)
			} else if (ecmType.startsWith("WD") || ecmType == "BUEWD") {
				logD("Type: BUEYD")
				category = 3
				rtLen = 135
				rtBuffer = IntArray(rtLen)
			} else if (ecmType.startsWith("YD") || ecmType == "BUEYD") {
				logD("Type: BUEYD")
				category = 3
				rtLen = 135
				rtBuffer = IntArray(rtLen)
			} else if (ecmType.startsWith("ZD") || ecmType == "BUEZD") {
				logD("Type: BUEZD")
				category = 3
				rtLen = 135
				rtBuffer = IntArray(rtLen)
			} else if (ecmType.startsWith("1D") || ecmType == "BUE1D" || ecmType == "B3R1D") {
				logD("Type: BUE1D")
				category = 3
				rtLen = 135
				rtBuffer = IntArray(rtLen)
			} else if (ecmType.startsWith("2D") || ecmType == "BUE2D") {
				logD("Type: BUE2D")
				category = 3
				rtLen = 135
				rtBuffer = IntArray(rtLen)
			} else if (ecmType.startsWith("3D") || ecmType == "BUE3D" || ecmType == "B3R3D") {
				logD("Type: BUE3D")
				category = 3
				rtLen = 135
				rtBuffer = IntArray(rtLen)
			} else {
				logW("unknown EcmType: $ecmType")
				binFile.close()
				mslFile.close()
				throw IOException("Unsupported ECM Type: $ecmType")
			}

			tabFileName = if (category == 1) {
				"/runtime1.tab"
			} else if (category == 2) {
				"/runtime2.tab"
			} else {
				"/runtime3.tab"
			}

			logD("Table: $tabFileName")

			try {
				offsetsTable = BufferedReader(InputStreamReader(Bin2MslConverter::class.java.getResourceAsStream(tabFileName)))

				numRecord = 0

				while (true) {
					val l = offsetsTable.readLine() ?: break
					val fields = p.split(l)

					val offset = fields[7].toInt()

					if (offset > rtLen - 3) {
						continue
					}

					alCategory.add(fields[1].toInt())
					alSecret.add(fields[3])
					alSize.add(fields[6].toInt())
					alOffset.add(offset)
					alScale.add(fields[9].toFloat())
					alTranslate.add(fields[10].toFloat())
					alFormat.add(fields[12])
					alExport.add(fields[17])

					if (fields[17] == "EGO Corr.") {
						offsEGOCorr = fields[7].toInt()
					} else if (fields[17] == "EGO1 Corr.") {
						offsEGOCorr1 = fields[7].toInt()
					} else if (fields[17] == "AFV") {
						offsAFV = fields[7].toInt()
					} else if (fields[17] == "AFV1") {
						offsAFV1 = fields[7].toInt()
					} else if (fields[17] == "sec" || fields[17] == "Seconds") {
						// no-op
					} else if (fields[17] == "status57" || fields[17] == "Flags1") {
						offsFlags1 = fields[7].toInt()
					} else if (fields[17] == "status58" || fields[17] == "Flags2") {
						offsFlags2 = fields[7].toInt()
					} else if (fields[17] == "Accel Corr.") {
						offsAccel = fields[7].toInt()
					} else if (fields[17] == "Decel Corr.") {
						// no-op
					} else if (fields[17] == "WUE") {
						offsWUE = fields[7].toInt()
					} else if (fields[17] == "TPS deg." || fields[17] == "TPD") {
						// no-op
					}

					numRecord++
				}

			} finally {
				offsetsTable?.close()
			}

			mslFile.print("\"EcmDroid/Bin2Msl $ecmType\"")
			mslFile.print("\r\n")

			if (category == 3) {
				mslFile.print("Number\tTime\tGego\tGego1\tEngine")
			} else {
				mslFile.print("Number\tTime\tGego\tEngine")
			}

			// Complete the Header
			for (idx in alExport.indices) {
				mslFile.print("\t" + alExport[idx])
			}
			mslFile.print("\r\n")

			var start = System.currentTimeMillis()
			try {

				numRecord = 1

				val rt = StringBuilder(1024)
				val df: NumberFormat = DecimalFormat.getInstance(loc)
				df.maximumFractionDigits = 3
				df.isGroupingUsed = false
				val rtb = rtBuffer!!
				while (!cancelled) {
					rt.setLength(0)

					// timestamp
					var iVal = binFile.readInt()
					var fVal = iVal.toFloat()

					// read RT data into buffer
					var checkSum = 0
					for (idx in 0 until rtLen) {
						rtb[idx] = binFile.readUnsignedByte()

						if (idx > 0 && idx < rtLen - 1) {
							checkSum = checkSum xor rtb[idx]
						}
					}

					// checksum comparison
					if (checkSum != rtb[rtLen - 1]) {
						numDiscard++
						continue
					}

					rt.append(df.format(numRecord.toLong()))
					rt.append(String.format(loc, "\t%.5f", fVal / 100.0))  // timestamp

					// Gego calculation
					if (rtb[offsFlags2] >= 128) {
						// closed loop
						iVal = rtb[offsEGOCorr]
						iVal += rtb[offsEGOCorr + 1] * 256
						fVal = iVal.toFloat()
					} else {
						// open loop
						iVal = rtb[offsAFV]
						iVal += rtb[offsAFV + 1] * 256
						fVal = iVal.toFloat()
					}

					rt.append('\t').append(df.format(fVal / 10.0))  // Gego Corr.

					if (category == 3) {

						if (rtb[offsFlags2] >= 128) {
							// closed loop
							iVal = rtb[offsEGOCorr1]
							iVal += rtb[offsEGOCorr1 + 1] * 256
							fVal = iVal.toFloat()
						} else {
							// open loop
							iVal = rtb[offsAFV1]
							iVal += rtb[offsAFV1 + 1] * 256
							fVal = iVal.toFloat()
						}

						rt.append('\t').append(df.format(fVal / 10.0))  // Gego1 Corr.
					}

					// engine byte
					var engine = 0

					// from: http://www.msextra.com/forums/viewtopic.php?f=98&t=31106
					// running:equ     0       ; 0 = engine not running            1 = running
					// crank:  equ     1       ; 0 = engine not cranking           1 = engine cranking
					// ASE:    equ     2       ; 0 = not in after start enrichment 1 = in after start enrichment
					// warmup: equ     3       ; 0 = not in warmup                 1 = in warmup
					// tpsaen: equ     4       ; 0 = not in TPS acceleration mode  1 = TPS acceleration mode
					// tpsden: equ     5       ; 0 = not in deacceleration mode    1 = in deacceleration mode
					// mapaen: equ     6       ; 0 = not in MAP acceleration mode  1 = MAP deaceeleration mode
					// idleOn: equ     7       ;

					// Flags1: 0=Engine_Run | 1=O2_Active | 2=Accel | 3=Decel  | 4=Run_Stop  | 4=WOT   | 6=Idle        | 7=Ignition_On
					// Engine: 0=Running    | 1=Crank     | 2=ASE   | 3=Warmup | 4=TPS-Accel | 5=Decel | 6=(MAP-Accel) | 7=(Idle)

					// startup enrichment (should be covered by WUE also)
					//if (rtBuffer[offsSeconds] <=20) {
					//  engine = engine | 4;
					//}

					// get warmup enrich and accel enrich from correction values

					// warmup (> 2%)
					if ((rtb[offsWUE] + rtb[offsWUE + 1] * 256) >= 1020) {
						engine = engine or 8
					}

					// acceleration enrichment (> 1%)
					// accel enrichment flag seems missing or too short,
					// so it disappears between the samples
					if ((rtb[offsAccel] + rtb[offsAccel + 1] * 256) >= 10) {
						engine = engine or 16
					}

					iVal = rtb[offsAccel] + rtb[offsAccel + 1] * 256

					// get states from flags1

					// Flags1: 0=Engine_Run | 1=O2_Active | 2=Accel | 3=Decel  | 4=Run_Stop  | 4=WOT   | 6=Idle        | 7=Ignition_On
					// Engine: 0=Running    | 1=Crank     | 2=ASE   | 3=Warmup | 4=TPS-Accel | 5=Decel | 6=(MAP-Accel) | 7=(Idle)

					iVal = rtb[offsFlags1]

					// running - Flag1, bit 0
					if ((iVal and 1) > 0) {
						engine = engine or 1
					}

					// accel - Flags1,  bit 2
					if ((iVal and 4) > 0) {
						engine = engine or 16
					}

					// decel - Flags1, bit 3
					if ((iVal and 8) > 0) {
						engine = engine or 32
					}

					// idle - Flags1, bit 6
					if ((iVal and 64) > 0) {
						engine = engine or 128
					}

					rt.append('\t').append(engine)               // engine byte

					// runtime data
					for (idx in alOffset.indices) {
						val o = alOffset[idx]
						iVal = rtb[o]

						if (alSize[idx] == 2) {
							iVal += rtb[o + 1] * 256
						}

						fVal = iVal.toFloat()
						fVal *= alScale[idx]
						fVal += alTranslate[idx]

						rt.append('\t')

						if (alFormat[idx] == "0") {
							rt.append(df.format(fVal.toInt().toLong()))
						} else {
							rt.append(df.format(fVal.toDouble()))
						}
					}
					rt.append("\r\n")
					mslFile.append(rt)
					numRecord++

					if (countObservers() > 0 || progressCallback != null) {
						val now = System.currentTimeMillis()
						if (start + OBSERVER_UPDATE_DELAY <= now) {
							if (countObservers() > 0) {
								setChanged()
								notifyObservers(String.format(Locale.US, "%d log records converted", numRecord))
							}
							progressCallback?.invoke(numRecord)
							start = now
						}
					}
				}
			} catch (e: EOFException) {
				val s = String.format(Locale.ENGLISH, "Conversion finished. %d of %d records discarded.", numDiscard, numRecord)
				logI(s)
				setChanged()
				notifyObservers(s)
			}
		} finally {
			binFile?.close()
			if (mslFile != null) {
				mslFile.flush()
				mslFile.close()
			}
		}
	}

	/**
	 * Cancel the conversion.
	 */
	fun cancel() {
		cancelled = true
	}

	private fun logD(msg: String) = logSink(Log.DEBUG, TAG, msg)
	private fun logI(msg: String) = logSink(Log.INFO, TAG, msg)
	private fun logW(msg: String) = logSink(Log.WARN, TAG, msg)

	companion object {
		private const val OBSERVER_UPDATE_DELAY = 500
		private const val TAG = "BIN2MSL"

		/**
		 * Log sink seam: (priority, tag, message) -> Unit. Defaults to routing
		 * through android.util.Log (unchanged behavior for the shipped app),
		 * but is settable so this class can be exercised on a plain JVM
		 * without touching the android.jar stub classpath -- the only
		 * Android coupling this class has.
		 */
		@JvmStatic
		var logSink: (Int, String, String) -> Unit = { priority, tag, message ->
			when (priority) {
				Log.INFO -> Log.i(tag, message)
				Log.WARN -> Log.w(tag, message)
				else -> Log.d(tag, message)
			}
		}
	}
}
