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
package biz.logicminds.buelltune;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import biz.logicminds.buelltune.activities.MainActivity;

import static org.junit.Assert.assertTrue;

/**
 * #10 residue (Android 13 install-blocked, {@code ecmdroid/ecmdroid#10} /
 * {@code logicminds/buelltune#3}): confirms, via a real {@link PackageManager}
 * query against the actually-installed test package - not source-level
 * manifest text - the two API-31+ manifest facts U11a's static review
 * already found present: {@code BLUETOOTH_SCAN}'s {@code neverForLocation}
 * flag and {@code MainActivity}'s explicit {@code exported=true}.
 * <p>
 * <b>Not executed in this sandbox</b> (no device/emulator available, no
 * {@code /dev/kvm}); verified to compile via
 * {@code ./gradlew assembleDebugAndroidTest}.
 */
@RunWith(AndroidJUnit4.class)
public class ManifestComplianceInstrumentedTest {

	private final Context context = ApplicationProvider.getApplicationContext();

	@Test
	public void bluetoothScanIsDeclaredNeverForLocationOnTheRealInstalledPackage() throws PackageManager.NameNotFoundException {
		PackageManager pm = context.getPackageManager();
		PackageInfo info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
		String[] permissions = info.requestedPermissions;
		int[] flags = info.requestedPermissionsFlags;
		assertTrue("Package must declare requested permissions", permissions != null && flags != null);

		int index = -1;
		for (int i = 0; i < permissions.length; i++) {
			if (Manifest.permission.BLUETOOTH_SCAN.equals(permissions[i])) {
				index = i;
				break;
			}
		}
		assertTrue("BLUETOOTH_SCAN must be a declared permission", index >= 0);
		assertTrue(
				"BLUETOOTH_SCAN must carry usesPermissionFlags=neverForLocation on the real installed package (R10/#10)",
				(flags[index] & PackageInfo.REQUESTED_PERMISSION_NEVER_FOR_LOCATION) != 0);
	}

	@Test
	public void mainActivityIsExportedOnTheRealInstalledPackage() throws PackageManager.NameNotFoundException {
		PackageManager pm = context.getPackageManager();
		ActivityInfo info = pm.getActivityInfo(new ComponentName(context, MainActivity.class), 0);
		assertTrue(
				"MainActivity must be exported for its LAUNCHER intent-filter to resolve on API 31+ (#10)",
				info.exported);
	}
}
