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
package biz.logicminds.buelltune.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import biz.logicminds.buelltune.R;

/**
 * Launcher entry point: shows the BuellTune brand banner on the app's dark
 * background for a short beat, then hands off to {@link MainActivity}.
 * Purely cosmetic - no ECM I/O, no permission prompts, non-interactive.
 */
public class SplashActivity extends Activity {

	private static final long DISPLAY_DURATION_MS = 1200;

	private final Handler handler = new Handler(Looper.getMainLooper());
	private final Runnable continueToMain = this::goToMainActivity;
	private boolean launched;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_splash);
		handler.postDelayed(continueToMain, DISPLAY_DURATION_MS);
	}

	@Override
	protected void onDestroy() {
		handler.removeCallbacks(continueToMain);
		super.onDestroy();
	}

	private void goToMainActivity() {
		if (launched || isFinishing()) {
			return;
		}
		launched = true;
		startActivity(new Intent(this, MainActivity.class));
		finish();
	}
}
