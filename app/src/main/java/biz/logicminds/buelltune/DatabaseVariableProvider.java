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

import biz.logicminds.buelltune.Variable.DataType;
import biz.logicminds.buelltune.data.BitNamesRow;
import biz.logicminds.buelltune.data.EcmDefinitionsDatabase;
import biz.logicminds.buelltune.data.EeVariableRow;
import biz.logicminds.buelltune.data.RtVariableRow;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.regex.Matcher;

/**
 * Create a Variable based on definitions in the built-in database.
 * <p>
 * Backed by Room (R6, KTD3) instead of the retired {@code DBHelper}. The
 * cache-then-DAO shape is unchanged: the {@link HashMap} caches still sit in
 * front of every lookup because these run on every poll cycle.
 */
public class DatabaseVariableProvider extends VariableProvider {

	private final EcmDefinitionsDatabase db;
	private HashMap<String, Variable> cache = new HashMap<String, Variable>();
	private String current_ecm = null;

	public DatabaseVariableProvider(Context ctx) {
		db = EcmDefinitionsDatabase.getInstance(ctx);
	}

	@Override
	public Collection<String> getRtVariableNames(String ecm) {
		return getRtVariableNames(ecm, null);
	}

	@Override
	public Collection<String> getScalarRtVariableNames(String ecm) {
		return getRtVariableNames(ecm, DataType.SCALAR);
	}

	@Override
	public Collection<String> getBitfieldRtVariableNames(String ecm) {
		return getRtVariableNames(ecm, DataType.BITFIELD);
	}

	private Collection<String> getRtVariableNames(String ecm, DataType type) {
		String typeUpper = type == null ? null : type.toString().toUpperCase(Locale.ENGLISH);
		return new LinkedList<String>(db.rtoffsetsDao().getRtVariableNames(ecm, typeUpper));
	}

	@Override
	public Variable getRtVariable(String ecm, String name) {
		if (ecm != null && !ecm.equals(current_ecm)) {
			cache.clear();
			current_ecm = ecm;
		}
		String key = "rt#" + name;
		Variable ret = cache.get(key);
		if (cache.containsKey(key)) {
			return ret;
		}
		ret = convert(db.rtoffsetsDao().getRtVariable(ecm, name));
		cache.put(key, ret);
		return ret;
	}

	@Override
	public Variable getEEPROMVariable(String ecm, String name) {
		if (ecm == null || name == null) {
			return null;
		}
		if (!ecm.equals(current_ecm)) {
			cache.clear();
			current_ecm = ecm;
		}
		String key = "ee#" + name;
		Variable ret = cache.get(key);
		if (cache.containsKey(key)) {
			return ret;
		}
		ret = convert(db.eeoffsetsDao().getEeVariable(ecm, name));
		cache.put(key, ret);
		return ret;
	}

	@Override
	public Variable getNearestEEPROMVariable(String ecm, int offset) {
		if (ecm == null) {
			return null;
		}
		return convert(db.eeoffsetsDao().getNearestEeVariable(ecm, offset));
	}

	@Override
	public String getName(String varname) {
		Matcher matcher = Constants.BIT_PATTERN.matcher(varname);
		if (matcher.matches()) {
			String name = matcher.group(1);
			int bit = Integer.parseInt(matcher.group(2).split(",")[0]);
			return getName(name, bit);
		}
		return db.namesDao().getName(varname);
	}

	@Override
	public String getName(String varname, int bit) {
		if (bit < 0 || bit > 7) {
			return null;
		}
		BitNamesRow row = db.bitsDao().getBitNames(varname);
		if (row == null) {
			return null;
		}
		switch (bit) {
			case 0: return row.getBitname1();
			case 1: return row.getBitname2();
			case 2: return row.getBitname3();
			case 3: return row.getBitname4();
			case 4: return row.getBitname5();
			case 5: return row.getBitname6();
			case 6: return row.getBitname7();
			default: return row.getBitname8();
		}
	}

	/**
	 * SQLite's TEXT-affinity-to-REAL coercion, applied to the ten
	 * numeric-looking {@code varchar} columns (KTD3.2): a null or
	 * unparseable string reads as {@code 0.0}, matching {@code
	 * Cursor.getDouble()}'s behavior on the same columns before the Room
	 * port.
	 */
	private static double parseDouble(String value) {
		if (value == null) {
			return 0.0;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return 0.0;
		}
	}

	/** Same coercion as {@link #parseDouble(String)}, truncated to int - matches {@code Cursor.getInt()} on a TEXT column. */
	private static int parseInt(String value) {
		return (int) parseDouble(value);
	}

	private Variable convert(RtVariableRow row) {
		if (row == null) {
			return null;
		}
		Variable ret = new Variable();
		ret.setId(row.getUniqueid());
		ret.setEcmType(ECM.Type.getType(row.getEcmType()));
		ret.setName(row.getOrigname());
		if (ret.getName() == null) {
			ret.setName(row.getVarname());
		}
		ret.setType(DataType.valueOf(row.getType().toUpperCase(Locale.ENGLISH)));
		ret.setSize(row.getSize());
		ret.setWidth(ret.getSize());
		ret.setOffset(row.getOffset());
		ret.setScale(parseDouble(row.getScale()));
		ret.setTranslate(parseDouble(row.getTranslate()));
		ret.setFormat(row.getFormat());
		ret.setLabel(row.getName());
		ret.setRemarks(row.getRemark());
		ret.setDescription(row.getDescription());
		ret.setUnit(row.getUnits());
		ret.setSymbol(Units.getSymbol(ret.getUnit()));
		ret.setLow(parseDouble(row.getLow()));
		ret.setHigh(parseDouble(row.getHigh()));
		ret.setUlow(parseInt(row.getUlow()));
		ret.setUhigh(parseInt(row.getUhigh()));
		ret.init();
		return ret;
	}

	private Variable convert(EeVariableRow row) {
		if (row == null) {
			return null;
		}
		Variable ret = new Variable();
		ret.setId(row.getUniqueid());
		ret.setEcmType(ECM.Type.getType(row.getEcmType()));
		ret.setName(row.getOrigname());
		if (ret.getName() == null) {
			ret.setName(row.getVarname());
		}
		ret.setType(DataType.valueOf(row.getType().toUpperCase(Locale.ENGLISH)));
		ret.setSize(row.getSize());
		ret.setWidth(row.getElemsize() == null ? 0 : row.getElemsize());
		ret.setCols(row.getCols() == null ? 0 : row.getCols());
		ret.setRows(row.getRows() == null ? 0 : row.getRows());
		ret.setOffset(row.getOffset());
		ret.setScale(parseDouble(row.getScale()));
		ret.setTranslate(parseDouble(row.getTranslate()));
		ret.setFormat(row.getFormat());
		ret.setLabel(row.getName());
		ret.setRemarks(row.getRemark());
		ret.setDescription(row.getDescription());
		ret.setUnit(row.getUnits());
		ret.setSymbol(Units.getSymbol(ret.getUnit()));
		ret.init();
		return ret;
	}
}
