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
package biz.logicminds.buelltune.fragments

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/**
 * A test-only `ContentProvider` standing in for a real document-picker
 * provider, declared only in `app/src/androidTest/AndroidManifest.xml` (it
 * never ships in the app APK). This is what lets #12/#7's regression-lock
 * tests hand [EEPROMFragment.onActivityResult] a genuinely *foreign*
 * `content://` URI - one the app under test does not own - rather than a
 * URI under the app's own storage, matching what `ACTION_OPEN_DOCUMENT`/
 * `ACTION_CREATE_DOCUMENT` actually return in production.
 *
 * [seed] registers a backing file's initial bytes before the stubbed
 * picker result is delivered; [openFile] honors the real SAF mode string
 * (`"r"`, `"w"`, ...) via [ParcelFileDescriptor.parseMode], so both the
 * load path (read) and the save path (write) exercise a real file
 * descriptor, not an in-memory shortcut. A key that was never [seed]ed
 * makes [openFile] throw [FileNotFoundException] - simulating a picked
 * document that has since become unreadable (revoked grant, deleted file,
 * unmounted volume): the exact surface
 * `EEPROMFragmentSafInstrumentedTest` uses to regression-lock
 * `onActivityResult`'s un-toasted outer catch blocks.
 */
class FixtureDocumentProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = files[uri.lastPathSegment]
            ?: throw FileNotFoundException("No fixture registered for $uri")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun getType(uri: Uri): String = "application/octet-stream"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "biz.logicminds.buelltune.androidtest.fixtures"
        private val files = ConcurrentHashMap<String, File>()

        fun uriFor(key: String): Uri = Uri.parse("content://$AUTHORITY/$key")

        /** Writes [bytes] to a fresh backing file registered under [key]; returns the resulting URI. */
        fun seed(key: String, bytes: ByteArray): Uri {
            val file = File.createTempFile("fixture-", "-$key")
            file.writeBytes(bytes)
            files[key] = file
            return uriFor(key)
        }

        /** Reads back whatever [openFile] has most recently written to [key]'s backing file, if any. */
        fun bytesWrittenTo(key: String): ByteArray? = files[key]?.readBytes()

        /** Deletes every backing file and forgets every registration; call from test teardown. */
        fun clear() {
            files.values.forEach { it.delete() }
            files.clear()
        }
    }
}
