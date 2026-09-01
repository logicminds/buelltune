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
package biz.logicminds.buelltune.fragments;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A test-only {@code ContentProvider} standing in for a real document-picker
 * provider, declared only in {@code app/src/androidTest/AndroidManifest.xml}
 * (it never ships in the app APK). This is what lets #12/#7's
 * regression-lock tests hand {@code EEPROMFragment.onActivityResult} a
 * genuinely *foreign* {@code content://} URI - one the app under test does
 * not own - rather than a URI under the app's own storage, matching what
 * {@code ACTION_OPEN_DOCUMENT}/{@code ACTION_CREATE_DOCUMENT} actually
 * return in production.
 * <p>
 * This provider is declared with no explicit {@code android:process}, so it
 * is hosted in its own isolated {@code biz.logicminds.buelltune.test}
 * process - a real, separate OS process/heap from the instrumented
 * {@code biz.logicminds.buelltune} process the test code itself runs in.
 * [seed]/[bytesWrittenTo]/[clear] therefore cannot be plain static-field
 * accessors (an earlier version was exactly that, and it silently no-opped:
 * "seeding" only ever populated the *test* process's own copy of the map,
 * never the copy the real cross-process {@link #openFile} call reads from -
 * confirmed via a real device run raising
 * {@code FileNotFoundException: No fixture registered}). Every one of them
 * instead round-trips through {@link #call}, the one {@code ContentProvider}
 * method designed for exactly this: an explicit Binder call into the
 * provider's own process.
 * <p>
 * Plain Java, also deliberately: a Kotlin class hosted in this
 * process crashes with {@code NoClassDefFoundError:
 * kotlin.jvm.internal.Intrinsics} the moment any compiler-generated
 * null-check runs, because AGP's androidTest dexer assumes test-APK code
 * always runs merged with the app-under-test's own classes (true for
 * instrumentation test code sharing that process, not for another
 * manifest-declared component in an isolated process of its own) and so
 * strips all of kotlin-stdlib - Intrinsics included - out of the test APK's
 * dex as an assumed duplicate. Confirmed via a real device run too.
 */
public class FixtureDocumentProvider extends ContentProvider {

    public static final String AUTHORITY = "biz.logicminds.buelltune.androidtest.fixtures";
    private static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);
    private static final String METHOD_SEED = "seed";
    private static final String METHOD_BYTES_WRITTEN_TO = "bytesWrittenTo";
    private static final String METHOD_CLEAR = "clear";
    private static final String EXTRA_BYTES = "bytes";

    /** Only ever populated inside this provider's own hosting process. */
    private static final Map<String, File> FILES = new ConcurrentHashMap<>();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = FILES.get(uri.getLastPathSegment());
        if (file == null) {
            throw new FileNotFoundException("No fixture registered for " + uri);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public String getType(Uri uri) {
        return "application/octet-stream";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public Bundle call(String method, String key, Bundle extras) {
        switch (method) {
            case METHOD_SEED: {
                try {
                    File file = File.createTempFile("fixture-", "-" + key);
                    Files.write(file.toPath(), extras.getByteArray(EXTRA_BYTES));
                    FILES.put(key, file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return null;
            }
            case METHOD_BYTES_WRITTEN_TO: {
                File file = FILES.get(key);
                if (file == null) {
                    return null;
                }
                try {
                    Bundle result = new Bundle();
                    result.putByteArray(EXTRA_BYTES, Files.readAllBytes(file.toPath()));
                    return result;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            case METHOD_CLEAR: {
                for (File file : FILES.values()) {
                    file.delete();
                }
                FILES.clear();
                return null;
            }
            default:
                return super.call(method, key, extras);
        }
    }

    /** Pure URI construction; no cross-process call needed. */
    public static Uri uriFor(String key) {
        return Uri.parse("content://" + AUTHORITY + "/" + key);
    }

    /** Writes {@code bytes} to a fresh backing file registered under {@code key}; returns the resulting URI. */
    public static Uri seed(Context context, String key, byte[] bytes) {
        Bundle extras = new Bundle();
        extras.putByteArray(EXTRA_BYTES, bytes);
        context.getContentResolver().call(AUTHORITY_URI, METHOD_SEED, key, extras);
        return uriFor(key);
    }

    /** Reads back whatever {@link #openFile} has most recently written to {@code key}'s backing file, if any. */
    public static byte[] bytesWrittenTo(Context context, String key) {
        Bundle result = context.getContentResolver().call(AUTHORITY_URI, METHOD_BYTES_WRITTEN_TO, key, null);
        return result != null ? result.getByteArray(EXTRA_BYTES) : null;
    }

    /** Deletes every backing file and forgets every registration; call from test teardown. */
    public static void clear(Context context) {
        context.getContentResolver().call(AUTHORITY_URI, METHOD_CLEAR, null, null);
    }
}
