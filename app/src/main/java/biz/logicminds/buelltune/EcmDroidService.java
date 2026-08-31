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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import biz.logicminds.buelltune.activities.MainActivity;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Service for continuously reading ECM runtime data and recording log files.
 */
public class EcmDroidService extends Service {
	public static final String REALTIME_DATA = "biz.logicminds.buelltune.Service.realtimedataevent";
	public static final String RECORDING_STARTED = "biz.logicminds.buelltune.Service.recording_started";
	public static final String RECORDING_STOPPED = "biz.logicminds.buelltune.Service.recording_stopped";
	public static final String CONNECTION_LOST = "biz.logicminds.buelltune.Service.connectionlost";
	public static final String TAG = "EcmDroidService";

	private static final int RECORDING_ID = 1;
	private static final Intent INTENT = new Intent(REALTIME_DATA);

	private final IBinder binder = new EcmDroidBinder();
	private NotificationManager nm;

	private boolean recording = false;
	private int recordingInterval;
	private long recordingStarted;
	private boolean reading;
	private ReaderThread readerThread;
	private DataOutputStream currentLog;
	private long bytesLogged;
	private long recordsLogged;
	private long readFailures;
	private ECM ecm;

	public class EcmDroidBinder extends Binder {
		public EcmDroidService getService() {
			return EcmDroidService.this;
		}
	}

	@Override
	public void onCreate() {
		ecm = ECM.getInstance(this);
		nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.cancel(RECORDING_ID); // Remove possible left-overs from a crash
		readerThread = new ReaderThread();
		readerThread.start();
		Log.d(TAG, "Service created.");
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		stopRecording();
		stopReading();
		readerThread.shutdown();
		Log.d(TAG, "Service destroyed.");
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.d(TAG, "Bound to service.");
		return binder;
	}

	public long getBytes() {
		return bytesLogged;
	}

	public long getRecords() {
		return recordsLogged;
	}

	public long getReadFailures() {
		return readFailures;
	}

	public float getLogsPerSecond() {
		return (float) (recordsLogged / (System.currentTimeMillis() - recordingStarted) * 1000.0);
	}

	/**
	 * Get the currently used Log file or null, if logging is not active.
	 */
	public File getCurrentFile() {
		return null;
	}

	public synchronized void startRecording(FileOutputStream logStream, int interval, ECM ecm) throws IOException {

		if (recording) {
			return;
		}
		this.recordingInterval = interval;
		sendBroadcast(new Intent(RECORDING_STARTED));
		bytesLogged = recordsLogged = readFailures = 0;
		currentLog = new DataOutputStream(logStream);
		String id = "UNKWN";
		if (ecm.getEEPROM() != null) {
			id = ecm.getEEPROM().getId();
		}
		currentLog.write(id.getBytes(), 0, 5);
		synchronized (readerThread) {
			recording = true;
			readerThread.notify();
		}
		Log.i(TAG, "Recording started.");
		ecm.setRecording(true);
		recordingStarted = System.currentTimeMillis();
		enterForeground(getString(R.string.app_name), getString(R.string.recording_started));
	}

	public synchronized void stopRecording() {
		recording = false;
		if (currentLog != null) {
			try {
				currentLog.flush();
				currentLog.close();
			} catch (IOException ioe) {
				Log.w(TAG, "Exception while flushing log stream. " + ioe);
			}
			currentLog = null;
		}
		Log.i(TAG, "Recording stopped.");
		ecm.setRecording(false);
		recordingInterval = 0;
		sendBroadcast(new Intent(RECORDING_STOPPED));
		if (reading) {
			// Still polling live data: keep the foreground notification up, but drop the recording-specific text.
			enterForeground(getString(R.string.app_name), getString(R.string.reading_started));
		} else {
			exitForeground();
		}
	}


	public boolean isRecording() {
		return recording;
	}

	public int getRecordingInterval() {
		return recordingInterval;
	}

	public synchronized void startReading() {
		synchronized (readerThread) {
			reading = true;
			readerThread.notify();
		}
		Log.i(TAG, "RT Data read started.");
		enterForeground(getString(R.string.app_name), getString(R.string.reading_started));
	}

	public synchronized void stopReading() {
		synchronized (readerThread) {
			reading = false;
			readerThread.notify();
		}
		Log.i(TAG, "RT Data read stopped.");
		if (recording) {
			// Still recording: keep the foreground notification up, restated with the recording text.
			enterForeground(getString(R.string.app_name), getString(R.string.recording_started));
		} else {
			exitForeground();
		}
	}

	private class ReaderThread extends Thread {
		private static final int DEFAULT_INTERVAL = 250;
		private static final int MINIMUM_INTERVAL = 50;
		private boolean running = true;

		private ReaderThread() {
			super("ECM-Reader-Thread");
		}

		@Override
		public void run() {
			long now;
			ECM ecm = ECM.getInstance(EcmDroidService.this);
			while (running) {
				if (!(ecm.isConnected() && (recording || reading))) {
					synchronized (this) {
						try {
							this.wait();
						} catch (InterruptedException e) {
							continue;
						}
					}
				}
				int i = Math.max(MINIMUM_INTERVAL, recordingInterval);
				now = System.currentTimeMillis();
				try {
					byte[] data = ecm.readRTData();
					sendBroadcast(INTENT);
					if (recording) {
						logPacket(data);
					}
				} catch (Exception e) {
					Log.d(TAG, "Log failed", e);
					readFailures++;
					if (i < DEFAULT_INTERVAL) {
						i = DEFAULT_INTERVAL;
					}
					if (!ecm.isConnected()) {
						Log.w(TAG, "ECM connection lost, stopping reading/recording.");
						reading = false;
						if (recording) {
							stopRecording();
						}
						sendBroadcast(new Intent(CONNECTION_LOST));
					}
				}
				if (running) {
					long toSleep = i - (System.currentTimeMillis() - now);
					if (toSleep > 0) {
						try {
							Thread.sleep(toSleep);
						} catch (InterruptedException e) {
							Log.i(TAG, "Reader Thread interrupted.");
							break;
						}
					}
				}
			}
			Log.d(TAG, "ReaderThread terminated.");
		}

		public void shutdown() {
			synchronized (this) {
				running = false;
				this.notify();
			}
			try {
				this.join();
			} catch (InterruptedException e) {
				Log.d(TAG, "Shutdown interrupted");
			}
		}
	}

	/**
	 * Enter (or refresh) the connectedDevice foreground state, entered when either live
	 * polling (startReading) or log recording (startRecording) begins, per R2/KD10/KTD10.
	 */
	private void enterForeground(String label, String text) {
		Notification notification = buildNotification(label, text);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			startForeground(RECORDING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
		} else {
			startForeground(RECORDING_ID, notification);
		}
	}

	/**
	 * Leave the foreground state. Only called once both polling and recording have stopped.
	 */
	private void exitForeground() {
		stopForeground(STOP_FOREGROUND_REMOVE);
	}

	private Notification buildNotification(String label, String text) {
		Bundle extras = new Bundle();
		extras.putInt(MainActivity.CURRENT_FRAGMENT, R.id.nav_log);
		Intent intent = new Intent(this, MainActivity.class);
		intent.putExtras(extras);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE);

		NotificationChannel channel = new NotificationChannel(
				"buelltune_logrecorder",
				"EcmDroid Log Recorder",
				NotificationManager.IMPORTANCE_DEFAULT);
		nm.createNotificationChannel(channel);
		Notification.Builder builder = new Notification.Builder(this, channel.getId())
				.setContentTitle(label)
				.setContentText(text)
				.setContentIntent(contentIntent)
				.setSmallIcon(R.drawable.ic_log);
		return builder.build();
	}

	private synchronized void logPacket(byte[] data) throws IOException {
		if (currentLog != null) {
			currentLog.writeInt((int) (System.currentTimeMillis() - recordingStarted) / 10);
			currentLog.write(data);
			bytesLogged += (data.length + 4);
			recordsLogged++;
		}
	}

	public boolean isReading() {
		return reading;
	}
}
