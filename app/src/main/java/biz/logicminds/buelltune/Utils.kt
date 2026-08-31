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

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Configuration
import android.os.Environment
import android.view.Menu

/**
 * Collection of various static utility methods.
 *
 * A single Kotlin `object` with `@JvmStatic` members (per U6/KTD8), not
 * split by Android-dependency: `MainActivity` calls `Utils.getAppVersion(this)`,
 * `PrefsActivity` calls `Utils.createOptionsMenu(this, menu)`, and
 * `ProgressDialogTask` calls `Utils.freezeOrientation(context)` - splitting
 * would change the call shape in those legacy files. [hexdump]/[toHex]/
 * [isEmptyString] are JVM-testable regardless, since the Android-touching
 * members are only *called* from Android code, not depended on by the pure
 * ones.
 */
object Utils {

    @JvmStatic
    fun createOptionsMenu(activity: Activity, menu: Menu): Boolean {
        // val mi = activity.menuInflater
        // mi.inflate(R.menu.main, menu)
        return true
    }

    @JvmStatic
    fun hexdump(data: ByteArray, offset: Int, len: Int): String {
        val sb = StringBuilder()
        for (i in 0 until len) {
            if (i + offset >= data.size) break
            sb.append(":").append(String.format("%02X", data[i + offset].toInt() and 0xFF))
        }
        return if (sb.isNotEmpty()) sb.substring(1) else "<empty>"
    }

    @JvmStatic
    fun hexdump(bytes: ByteArray): String = hexdump(bytes, 0, bytes.size)

    @JvmStatic
    fun isEmptyString(str: Any?): Boolean = str == null || str.toString().trim().isEmpty()

    @JvmStatic
    fun toHex(i: Int, vararg width: Int): String {
        val fmt = "%0" + (if (width.size == 1) width[0] else 2) + "X"
        return String.format(fmt, i)
    }

    @JvmStatic
    fun freezeOrientation(context: Activity): Int {
        val result = context.requestedOrientation
        when (context.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Configuration.ORIENTATION_PORTRAIT -> context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
        }
        return result
    }

    @JvmStatic
    fun getAppVersion(context: Context): String? {
        var result: String? = null
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            result = context.getText(R.string.app_name).toString() + " " + pInfo.versionName
        } catch (e: NameNotFoundException) {
        }
        return result
    }

    @JvmStatic
    fun isExternalStorageAvailable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }
}
