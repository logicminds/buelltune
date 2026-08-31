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

import android.content.Context;

import biz.logicminds.buelltune.Constants.DataSource;
import biz.logicminds.buelltune.data.BitSetRow;
import biz.logicminds.buelltune.data.EcmDefinitionsDatabase;

import java.util.HashMap;

/**
 * Create a BitSet based on definitions in the built-in database.
 * <p>
 * Backed by Room (R6, KTD3) instead of the retired {@code DBHelper}. The
 * {@code rtoffsets}/{@code eeoffsets} table switch driven by {@link
 * DataSource} is preserved by calling the matching DAO.
 */
public class DatabaseBitSetProvider extends BitSetProvider {

	private final EcmDefinitionsDatabase db;
	private HashMap<String, BitSet> cache;
	private String current_ecm = null;

	public DatabaseBitSetProvider(Context ctx) {
		db = EcmDefinitionsDatabase.getInstance(ctx);
		cache = new HashMap<String, BitSet>();
	}

	@Override
	public BitSet getBitSet(String ecm_id, String name, DataSource source) {
		if (ecm_id != null && !ecm_id.equals(current_ecm)) {
			cache.clear();
			current_ecm = ecm_id;
		}
		BitSet ret = cache.get(name);
		if (cache.containsKey(name)) {
			return ret;
		}
		BitSetRow row = (source == DataSource.EEPROM)
				? db.eeoffsetsDao().getBitSetRow(ecm_id, name)
				: db.rtoffsetsDao().getBitSetRow(ecm_id, name);
		if (row != null) {
			ret = new BitSet(row.getVarname(), row.getName(), row.getOffset());
			String[] bitnames = {
					row.getBitname1(), row.getBitname2(), row.getBitname3(), row.getBitname4(),
					row.getBitname5(), row.getBitname6(), row.getBitname7(), row.getBitname8()
			};
			String[] bitdescs = {
					row.getBit1(), row.getBit2(), row.getBit3(), row.getBit4(),
					row.getBit5(), row.getBit6(), row.getBit7(), row.getBit8()
			};
			String[] dtcs = {
					row.getDtc1(), row.getDtc2(), row.getDtc3(), row.getDtc4(),
					row.getDtc5(), row.getDtc6(), row.getDtc7(), row.getDtc8()
			};
			Integer byteNr = row.getByte();
			for (int i = 1; i <= 8; i++) {
				String bitname = bitnames[i - 1];
				String bitdesc = bitdescs[i - 1];
				if (Utils.isEmptyString(bitname) && Utils.isEmptyString(bitdesc)) {
					continue;
				}
				if (Utils.isEmptyString(bitname)) {
					bitname = row.getVarname() + "." + i;
				}
				Bit bit = new Bit();
				bit.setName(bitname);
				bit.setBitNr(i - 1);
				bit.setByteNr(byteNr == null ? 0 : byteNr);
				bit.setOffset(row.getOffset());
				bit.setType(ECM.Type.getType(row.getType()));
				bit.setRemark(bitdesc);
				bit.setCode(dtcs[i - 1]);
				ret.add(bit);
			}
		}
		cache.put(name, ret);
		return ret;
	}

}
