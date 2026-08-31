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

import android.Manifest;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;

import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import biz.logicminds.buelltune.BuildConfig;
import biz.logicminds.buelltune.Constants;
import biz.logicminds.buelltune.DBHelper;
import biz.logicminds.buelltune.ECM;
import biz.logicminds.buelltune.EcmDroidService;
import biz.logicminds.buelltune.R;
import biz.logicminds.buelltune.Utils;
import biz.logicminds.buelltune.fragments.ActiveTestsFragment;
import biz.logicminds.buelltune.fragments.DataChannelFragment;
import biz.logicminds.buelltune.fragments.EEPROMFragment;
import biz.logicminds.buelltune.fragments.LogFragment;
import biz.logicminds.buelltune.fragments.MainFragment;
import biz.logicminds.buelltune.fragments.SetupFragment;
import biz.logicminds.buelltune.fragments.TorqueValuesFragment;
import biz.logicminds.buelltune.fragments.TroubleCodeFragment;
import biz.logicminds.buelltune.task.FetchTask;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import de.kai_morich.simple_bluetooth_le_terminal.DevicesFragment;

public class MainActivity extends AppCompatActivity
		implements NavigationView.OnNavigationItemSelectedListener {

	public static final String CURRENT_FRAGMENT = "currentFragment";
	private static final String TAG = "MAIN";
	private static final ColorStateList TINT_DISCONNECTED = ColorStateList.valueOf(Color.RED);
	private static final ColorStateList TINT_CONNECTING = ColorStateList.valueOf(Color.GRAY);
	private static final ColorStateList TINT_CONNECTED = ColorStateList.valueOf(Color.rgb(0x00, 0xdd, 0x00));

	private int currentFragment = R.id.nav_info;
	private DBHelper dbHelper;

	private ECM ecm = ECM.getInstance(this);
	protected EcmDroidService ecmDroidService;
	private FloatingActionButton fab;

	private boolean isTransactionSafe;
	private boolean isTransactionPending;

	private UsbSerialPort port;
	private SerialInputOutputManager usbIoManager;
	private ServiceConnection serviceConnection = new ServiceConnection() {

		public void onServiceDisconnected(ComponentName name) {
			Log.d(TAG, "Disconnected from Service");
			ecmDroidService = null;
		}

		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.d(TAG, "Connected to Log Service");
			ecmDroidService = ((EcmDroidService.EcmDroidBinder) service).getService();
		}
	};

	private BroadcastReceiver connectionLostReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.w(TAG, "Connection to ECM lost.");
			updateConnectButton();
			Toast.makeText(MainActivity.this, R.string.connection_lost, Toast.LENGTH_LONG).show();
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate(" + savedInstanceState + "," + getIntent().getExtras() + ")");
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			currentFragment = savedInstanceState.getInt(CURRENT_FRAGMENT, R.id.nav_info);
		} else if (getIntent().getExtras() != null) {
			currentFragment = getIntent().getExtras().getInt(CURRENT_FRAGMENT, R.id.nav_info);
		}

		setContentView(R.layout.activity_main);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		fab = (FloatingActionButton) findViewById(R.id.fab);
		fab.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (!ecm.isConnected()) {
					connect();
				} else {
					disconnect();
				}
			}
		});


		applyEdgeToEdgeInsets();

		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
				this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
		drawer.addDrawerListener(toggle);
		toggle.syncState();

		// targetSdk 36 enables predictive back by default; onBackPressed() is no longer invoked
		// for back gestures, so the drawer-close behavior moves to the dispatcher (R3).
		getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				if (drawer.isDrawerOpen(GravityCompat.START)) {
					drawer.closeDrawer(GravityCompat.START);
				} else {
					setEnabled(false);
					getOnBackPressedDispatcher().onBackPressed();
					setEnabled(true);
				}
			}
		});

		NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
		navigationView.setNavigationItemSelectedListener(this);

		((TextView) navigationView.getHeaderView(0).findViewById(R.id.headerTitle)).setText(Utils.getAppVersion(this));

		// Bind to our service and setup the connect button
		bindService(new Intent(this, EcmDroidService.class), serviceConnection, Context.BIND_AUTO_CREATE);
		startService(new Intent(this, EcmDroidService.class));

		switchToFragment(currentFragment);

		// Install the database
		dbHelper = new DBHelper(this);
	}

	/**
	 * Mandatory edge-to-edge display (targetSdk 35+) means the window no longer reserves
	 * space for the status/navigation bars; pad the activity chrome ourselves so no legacy
	 * screen is occluded (R3, R4). Applied once at the content-frame/app-bar/FAB level, not
	 * per Fragment, so R13's "no screen logic edited" constraint holds.
	 */
	private void applyEdgeToEdgeInsets() {
		View drawerLayout = findViewById(R.id.drawer_layout);
		View appBarLayout = findViewById(R.id.app_bar_layout);
		View contentFrame = findViewById(R.id.content_frame);
		View fabView = findViewById(R.id.fab);

		final int appBarPaddingLeft = appBarLayout.getPaddingLeft();
		final int appBarPaddingRight = appBarLayout.getPaddingRight();
		final int appBarPaddingBottom = appBarLayout.getPaddingBottom();

		final int contentPaddingLeft = contentFrame.getPaddingLeft();
		final int contentPaddingTop = contentFrame.getPaddingTop();
		final int contentPaddingRight = contentFrame.getPaddingRight();
		final int contentPaddingBottom = contentFrame.getPaddingBottom();

		ViewGroup.MarginLayoutParams fabParams = (ViewGroup.MarginLayoutParams) fabView.getLayoutParams();
		final int fabMarginBottom = fabParams.bottomMargin;
		final int fabMarginEnd = fabParams.getMarginEnd();

		ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (view, windowInsets) -> {
			Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

			appBarLayout.setPadding(appBarPaddingLeft + systemBars.left, systemBars.top,
					appBarPaddingRight + systemBars.right, appBarPaddingBottom);

			contentFrame.setPadding(contentPaddingLeft + systemBars.left, contentPaddingTop,
					contentPaddingRight + systemBars.right, contentPaddingBottom + systemBars.bottom);

			ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) fabView.getLayoutParams();
			params.bottomMargin = fabMarginBottom + systemBars.bottom;
			params.setMarginEnd(fabMarginEnd + systemBars.right);
			fabView.setLayoutParams(params);

			return windowInsets;
		});
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			// Resume the interrupted action rather than leaving the rider's first tap consumed (F2, R10).
			showDevices();
		} else {
			Toast.makeText(this, R.string.bluetooth_connect_permission_denied, Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateConnectButton();
		registerReceiver(connectionLostReceiver, new IntentFilter(EcmDroidService.CONNECTION_LOST), Context.RECEIVER_NOT_EXPORTED);
	}

	@Override
	protected void onPostResume() {
		super.onPostResume();
		isTransactionSafe = true;
		if (isTransactionPending) {
			switchToFragment(currentFragment);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		isTransactionSafe = false;
		unregisterReceiver(connectionLostReceiver);
	}

	@Override
	protected void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		state.putInt(CURRENT_FRAGMENT, currentFragment);
	}

	@Override
	protected void onDestroy() {
		unbindService(serviceConnection);
		super.onDestroy();
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item) {
		// Handle navigation view item clicks here.
		int id = item.getItemId();

		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		drawer.closeDrawer(GravityCompat.START);

		if (id == R.id.nav_torque) {
			Intent intent = new Intent(this, TorqueValuesFragment.class);
			startActivity(intent);
		} else if (id == R.id.nav_settings) {
			Intent intent = new Intent(this, PrefsActivity.class);
			startActivity(intent);
		} else if (id == R.id.nav_about) {
			Intent intent = new Intent(this, AboutActivity.class);
			startActivity(intent);
		} else {
			currentFragment = id;
			switchToFragment(id);
		}
		return true;
	}

	private void switchToFragment(int id) {
		if (isTransactionSafe) {
			Fragment fragment = null;
			if (id == R.id.nav_info) {
				fragment = new MainFragment();
			} else if (id == R.id.nav_troublecodes) {
				fragment = new TroubleCodeFragment();
			} else if (id == R.id.nav_tests) {
				fragment = new ActiveTestsFragment();
			} else if (id == R.id.nav_datachannels) {
				fragment = new DataChannelFragment();
			} else if (id == R.id.nav_setup) {
				fragment = new SetupFragment();
			} else if (id == R.id.nav_log) {
				fragment = new LogFragment();
			} else if (id == R.id.nav_eeprom) {
				fragment = new EEPROMFragment();
			}
			FragmentManager mgr = getFragmentManager();
			if (fragment != null) {
				Log.d(TAG, "Switching to fragment id " + id);
				mgr.beginTransaction()
						.replace(R.id.content_frame, fragment)
						.commit();
				isTransactionPending = false;
				updateConnectButton();
			}
		} else {
			isTransactionPending = true;
			Log.d(TAG, "Postponing fragment switch action");
		}
	}

	private void showDevices() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.paired_devices);
		final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				Log.w(TAG, "Requesting BT connect permissions...");
				ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
				return;
			}
		}
		final BluetoothDevice[] devices = btAdapter == null ? new BluetoothDevice[0] : btAdapter.getBondedDevices().toArray(new BluetoothDevice[0]);
		CharSequence[] items = new CharSequence[devices.length];

		int i = 0;
		for (BluetoothDevice device : devices) {
			items[i++] = device.getName() + " (" + device.getAddress() + ")";
		}

		builder.setItems(items, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				dialog.cancel();
				connect(devices[item]);
			}
		});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private void findCOMDevice() {
		// Find all available drivers from attached devices.
		UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
		List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
		if (availableDrivers.isEmpty()) {
			Toast.makeText(MainActivity.this, "No USB COM Devices available", Toast.LENGTH_LONG).show();
			return;
		}

		// Open a connection to the first available driver.
		UsbSerialDriver driver = availableDrivers.get(0);
		UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
		if (connection == null) {
			int flags = PendingIntent.FLAG_MUTABLE;
			Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".GRANT_USB");
			intent.setPackage(this.getPackageName());
			PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, intent, flags);
			manager.requestPermission(driver.getDevice(), usbPermissionIntent);
			Toast.makeText(MainActivity.this, "Give USB Permission and try again.", Toast.LENGTH_LONG).show();
			//TODO make this stick between runs.
			return;
		}

		port = driver.getPorts().get(0); // Most devices have just one port (port 0)
		//Toast.makeText(MainActivity.this, String.format(Locale.US, "Found %s",port.getDevice().getProductName()), Toast.LENGTH_SHORT).show();
		try {
			port.open(connection);
			int baud = ECM.Protocol.FACTORY_RACE.equals(getProtocol()) ? 19200 : 9600;
			port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
			connect(port);
        } catch (IOException e) {
			Toast.makeText(MainActivity.this, "Could not open COM port.", Toast.LENGTH_LONG).show();
            throw new RuntimeException(e);
        }
	}
	private void disconnect() {
		try {
			if (ecmDroidService != null && ecmDroidService.isRecording()) {
				ecmDroidService.stopRecording();
			}
			ecm.disconnect();
			Toast.makeText(MainActivity.this, R.string.disconnected, Toast.LENGTH_LONG).show();
			// Reload fragment
			switchToFragment(currentFragment);
		} catch (IOException ioe) {
			Log.w(TAG, "Disconnect failed. ", ioe);
		}
	}

	private void connect() {
		fab.setBackgroundTintList(TINT_CONNECTING);
		fab.setImageResource(R.drawable.ic_connected);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
		String connectionType = prefs.getString(getString(R.string.prefs_conn_type), getString(R.string.prefs_bt_connection));
		if (getString(R.string.prefs_bt_connection).equals(connectionType)) {
			BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
			if (adapter == null || !adapter.isEnabled()) {
				Toast.makeText(MainActivity.this, R.string.bluetooth_is_not_available, Toast.LENGTH_LONG).show();
				return;
			}
			showDevices();
		} else if ("BLE".equals(connectionType)) {
			fab.hide();
			Fragment fragment = new DevicesFragment();
			getFragmentManager().beginTransaction().replace(R.id.content_frame, fragment, "devices").addToBackStack("blescan").commit();
		} else if ("COM".equals(connectionType)) {
			findCOMDevice();
		} else {
			String host = prefs.getString("tcp_host", null);
			int port = 0;
			try {
				port = Integer.parseInt(prefs.getString("tcp_port", "0"));
			} catch (NumberFormatException nfe) {
			}

			if (host == null || port <= 0 || port > 0xFFFF) {
				Toast.makeText(MainActivity.this, String.format(Locale.US, "%s/%d: Illegal host/port combination.", host, port), Toast.LENGTH_LONG).show();
				return;
			}
			connect(host, port);
		}
	}

	public void connect(BluetoothDevice bluetoothDevice) {
		Log.i(TAG, "Device selected: " + bluetoothDevice);
		new ConnectTask(bluetoothDevice, getProtocol(), false).execute();
	}

	public void connectBLE(BluetoothDevice bluetoothDevice) {
		Log.i(TAG, "BLE Device selected: " + bluetoothDevice);
		new ConnectTask(bluetoothDevice, getProtocol(), true).execute();
	}

	private void connect(String host, int port) {
		Log.i(TAG, "TCP Connection to " + host + ":" + port);
		new ConnectTask(host, port, getProtocol()).execute();
	}

	public void connect(UsbSerialPort uartDevice) {
		Log.i(TAG, "Device selected: " + uartDevice);
		new ConnectTask(uartDevice, getProtocol()).execute();
	}

	private ECM.Protocol getProtocol() {
		return ECM.Protocol.values()[PreferenceManager.getDefaultSharedPreferences(this).getInt(Constants.PREFS_ECM_PROTOCOL, 0)];
	}

	private class ConnectTask extends FetchTask {
		private ECM.Protocol protocol;
		private BluetoothDevice btDevice;
		private boolean ble;
		private String host;
		private int port;
		private UsbSerialPort uart;

		public ConnectTask(BluetoothDevice device, ECM.Protocol protocol, boolean ble) {
			super(MainActivity.this);
			this.ble = ble;
			btDevice = device;
			this.protocol = protocol;
		}
		public ConnectTask(UsbSerialPort uart, ECM.Protocol protocol) {
			super(MainActivity.this);
			this.uart = uart;
			this.protocol = protocol;
		}

		public ConnectTask(String host, int port, ECM.Protocol protocol) {
			super(MainActivity.this);
			this.host = host;
			this.port = port;
			this.protocol = protocol;

		}

		@Override
		protected void onPreExecute() {
			// connectButton.setEnabled(false);
			super.onPreExecute();
		}

		@Override
		protected Exception doInBackground(Void... v) {
			String target = null;
			if (btDevice != null) {
				target = btDevice.getName();
			} else {
				target = host + ":" + port;
			}
			publishProgress(String.format(Locale.US, "Connecting to %1$s...", target));
			try {
				if (btDevice != null) {
					if (ble) {
						ecm.connect(this.context, btDevice, protocol);
					} else {
						ecm.connect(btDevice, protocol);
					}
				} else if (uart != null) {
					ecm.connect(uart, protocol);
				}
				else {
					ecm.connect(host, port, protocol);
				}
			} catch (Exception e) {
				return e;
			}
			return super.doInBackground();
		}

		@Override
		protected void onProgressUpdate(String... values) {
			// update();
			super.onProgressUpdate(values);
		}

		@Override
		protected void onPostExecute(Exception result) {
			super.onPostExecute(result);
			if (result != null) {
				try {
					ecm.disconnect();
				} catch (IOException e) {
				}
			}
			// Reload the fragment
			switchToFragment(currentFragment);
			// FIXME updateConnectButton();
		}
	}

	public void updateConnectButton() {
		fab.hide();
		fab.setBackgroundTintList(ecm.isConnected() ? TINT_CONNECTED : TINT_DISCONNECTED);
		fab.setImageResource(ecm.isConnected() ? R.drawable.ic_connected : R.drawable.ic_disconnected);
		fab.show();
	}
}
