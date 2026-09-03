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

import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;

import biz.logicminds.buelltune.AppPreferences;
import biz.logicminds.buelltune.ECM;
import biz.logicminds.buelltune.EEPROM;
import biz.logicminds.buelltune.R;
import biz.logicminds.buelltune.activities.MainActivity;

public class MainFragment extends Fragment {
	private static final String TAG = "MAIN";

	private ECM ecm;
	private Spinner protocolSpinner;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ecm = ECM.getInstance(getActivity());

		if (getArguments() != null) {
			String deviceAddress = getArguments().getString("device");
			if (deviceAddress != null) {
				Log.i(TAG, "Trying to connect to " + deviceAddress);
				final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
				MainActivity activity = (MainActivity) getActivity();
				activity.connectBLE(btAdapter.getRemoteDevice(deviceAddress));
			}
		}
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.main, container, false);

		protocolSpinner = (Spinner) view.findViewById(R.id.protocolSpinner);
		protocolSpinner.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, ECM.Protocol.values()));
		protocolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				AppPreferences.setEcmProtocolIndex(getActivity(), position);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {

			}
		});

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
		MainActivity activity = (MainActivity) getActivity();
		activity.updateConnectButton();
		activity.setTitle(getString(R.string.ecm_information));
		protocolSpinner.setEnabled(!ecm.isConnected());
		protocolSpinner.setSelection(ecm.getCurrentProtocol().ordinal());
		update();
	}

	private void update() {
		setText(R.id.ecmIdValue, ecm.getId());
		EEPROM eeprom = ecm.getEEPROM();
		if (eeprom != null) {
			setText(R.id.ecmVersionValue, eeprom.getVersion() == null ? "-" : eeprom.getVersion());
			setText(R.id.eepromSizeValue, "" + eeprom.length());
			setText(R.id.eepromPagesValue, "" + eeprom.getPageCount());
			setText(R.id.ecmTypeValue, "" + ecm.getType());
			if (eeprom.isEepromRead()) {
				setText(R.id.ecmSerialValue, ecm.getSerialNo());
				setText(R.id.ecmMfgDateValue, ecm.getMfgDate());
				setText(R.id.ecmLayoutRevisionValue, ecm.getLayoutRevision());
				setText(R.id.ecmCountryIdValue, ecm.getCountryId());
				setText(R.id.ecmCalibrationValue, ecm.getCalibrationId());
			}
		}
	}

	private void setText(int id, String text) {
		View v = getView().findViewById(id);
		if (v instanceof TextView) {
			((TextView) v).setText(text);
		}
	}
}
