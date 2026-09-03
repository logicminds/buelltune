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
package biz.logicminds.buelltune.chat

import biz.logicminds.buelltune.Constants
import biz.logicminds.buelltune.ECM
import biz.logicminds.buelltune.EcmDefinitionsProvider
import biz.logicminds.buelltune.Error
import biz.logicminds.buelltune.Error.ErrorType
import biz.logicminds.buelltune.Variable
import biz.logicminds.buelltune.VariableProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.util.Locale

/**
 * Read-only facade over [ECM]/[VariableProvider] exposing exactly the six
 * tools the chat agent is allowed to call (R1, R3, R8, KTD2, KTD4). Plain
 * Kotlin, no Koog import - a thin adapter (U5) wraps each `suspend fun`
 * below as a native Koog `Tool`.
 *
 * Every method:
 *  - runs on [Dispatchers.IO] (R4) since a call may queue behind an
 *    in-flight poll cycle on the mutex-serialized transport;
 *  - starts by checking [ECM.isConnected] and returns
 *    [ToolResult.notConnected] instead of throwing/blocking when it isn't
 *    (R7, AE2);
 *  - never issues a subscription against `PollRecordLoop.runtimeData`
 *    (KTD3) - [readLiveData] calls [ECM.readRTData] itself, once per call;
 *  - returns compact, unit-labeled JSON rather than raw bytes (R6).
 *
 * [definitionsProvider] is accepted per this unit's constructor contract
 * (parallels [ECM]'s own constructor shape) even though none of the six
 * tools below need the EEPROM page-layout skeleton it resolves - every
 * lookup here goes through the already-fetched [ECM.getEEPROM]/
 * [VariableProvider] instead.
 */
class EcmTools(
    private val ecm: ECM,
    private val variableProvider: VariableProvider,
    @Suppress("unused") private val definitionsProvider: EcmDefinitionsProvider,
) {

    /** `get_ecm_info` (R3): identity of the connected ECM and its link. */
    suspend fun getEcmInfo(): ToolResult = withContext(Dispatchers.IO) {
        if (!ecm.isConnected()) return@withContext ToolResult.notConnected()
        ToolResult.ok(
            buildJsonObject {
                put("ecmId", ecm.getEEPROM()?.id)
                put("version", ecm.getVersion())
                put("protocol", ecm.getCurrentProtocol().toString())
                put("transport", ecm.getTransport()?.let { it::class.simpleName })
            },
        )
    }

    /** `list_live_variables` (R3, R5): names + units only, no values. */
    suspend fun listLiveVariables(): ToolResult = withContext(Dispatchers.IO) {
        if (!ecm.isConnected()) return@withContext ToolResult.notConnected()
        val ecmId = ecm.getId()
            ?: return@withContext ToolResult.error("ECM id is unknown; EEPROM has not been read yet.")
        val names = variableProvider.getRtVariableNames(ecmId)
        val variables = buildJsonArray {
            for (name in names) {
                add(
                    buildJsonObject {
                        put("name", name)
                        put("unit", variableProvider.getRtVariable(ecmId, name)?.unit)
                    },
                )
            }
        }
        ToolResult.ok(buildJsonObject { put("variables", variables) })
    }

    /**
     * `read_live_data` (R3, R5, R6, KTD3): a single [ECM.readRTData] call,
     * then each requested name is resolved and scaled individually. An
     * unknown/unresolvable name becomes a per-item `error` entry in the
     * result array (R5) rather than failing the whole call.
     */
    suspend fun readLiveData(variables: List<String>): ToolResult = withContext(Dispatchers.IO) {
        if (!ecm.isConnected()) return@withContext ToolResult.notConnected()
        val ecmId = ecm.getId()
            ?: return@withContext ToolResult.error("ECM id is unknown; EEPROM has not been read yet.")
        val rtData = try {
            ecm.readRTData()
        } catch (e: IOException) {
            return@withContext ToolResult.error("Failed to read runtime data: ${e.message}")
        }
        val values = buildJsonArray {
            for (name in variables) {
                val variable = variableProvider.getRtVariable(ecmId, name)
                if (variable == null) {
                    add(
                        buildJsonObject {
                            put("name", name)
                            put("error", "Unknown live variable '$name'.")
                        },
                    )
                } else {
                    variable.refreshValue(rtData)
                    add(
                        buildJsonObject {
                            put("name", name)
                            put("value", variable.getFormattedValue())
                            put("unit", variable.unit)
                        },
                    )
                }
            }
        }
        ToolResult.ok(buildJsonObject { put("values", values) })
    }

    /** `read_error_codes` (R3, R6): both current and stored codes. */
    suspend fun readErrorCodes(): ToolResult = withContext(Dispatchers.IO) {
        if (!ecm.isConnected()) return@withContext ToolResult.notConnected()
        val current = try {
            ecm.getErrors(ErrorType.CURRENT)
        } catch (e: IOException) {
            return@withContext ToolResult.error("Failed to read current error codes: ${e.message}")
        }
        val stored = try {
            ecm.getErrors(ErrorType.STORED)
        } catch (e: IOException) {
            return@withContext ToolResult.error("Failed to read stored error codes: ${e.message}")
        }
        ToolResult.ok(
            buildJsonObject {
                put("current", errorsToJson(current))
                put("stored", errorsToJson(stored))
            },
        )
    }

    /**
     * `get_eeprom_parameter` (R3, R6, R7): checked ahead of connection use
     * against "EEPROM never fetched" - distinct from, and never conflated
     * with, [ToolResult.notConnected] (plan's explicit U2 requirement).
     */
    suspend fun getEepromParameter(name: String): ToolResult = withContext(Dispatchers.IO) {
        if (!ecm.isConnected()) return@withContext ToolResult.notConnected()
        if (ecm.getEEPROM()?.isEepromRead() != true) return@withContext ToolResult.eepromNotRead()
        val variable = ecm.getEEPROMValue(name)
            ?: return@withContext ToolResult.error("Unknown EEPROM parameter '$name'.")
        ToolResult.ok(
            buildJsonObject {
                put("name", name)
                put("value", variable.getFormattedValue())
                put("unit", variable.unit)
            },
        )
    }

    /**
     * `get_fuel_map_region` (R3, R6, R7): [rpmRange]/[tpsRange] are 0-indexed
     * row/column indices into the front/rear fuel table, not physical
     * RPM/TPS values - no axis interpolation happens here. Cells are raw
     * pulse-width units; the 58µs-per-unit conversion is intentionally left
     * for the system prompt to explain to the model, not computed here.
     */
    suspend fun getFuelMapRegion(cylinder: String, rpmRange: IntRange, tpsRange: IntRange): ToolResult =
        withContext(Dispatchers.IO) {
            if (!ecm.isConnected()) return@withContext ToolResult.notConnected()
            if (ecm.getEEPROM()?.isEepromRead() != true) return@withContext ToolResult.eepromNotRead()
            val variableName = when (cylinder.lowercase(Locale.ROOT)) {
                "front" -> Constants.Variables.Tab_Fuel_Front
                "rear" -> Constants.Variables.Tab_Fuel_Rear
                else -> return@withContext ToolResult.error("Unknown cylinder '$cylinder'; expected 'front' or 'rear'.")
            }
            val variable = ecm.getEEPROMValue(variableName)
                ?: return@withContext ToolResult.error("Fuel map table '$variableName' is not available for this ECM.")
            if (variable.type != Variable.DataType.TABLE && variable.type != Variable.DataType.MAP) {
                return@withContext ToolResult.error("'$variableName' is not a 2-D fuel map table.")
            }
            if (rpmRange.isEmpty() || tpsRange.isEmpty() ||
                rpmRange.first < 0 || rpmRange.last >= variable.rows ||
                tpsRange.first < 0 || tpsRange.last >= variable.cols
            ) {
                return@withContext ToolResult.error(
                    "Row/column range out of bounds: rows 0..${variable.rows - 1}, cols 0..${variable.cols - 1}.",
                )
            }
            val cells = buildJsonArray {
                for (row in rpmRange) {
                    add(
                        buildJsonArray {
                            for (col in tpsRange) {
                                add(JsonPrimitive(variable.getIntValueAt(row, col)))
                            }
                        },
                    )
                }
            }
            ToolResult.ok(
                buildJsonObject {
                    put("cylinder", cylinder)
                    put("variable", variableName)
                    put("rowRange", buildJsonArray { add(JsonPrimitive(rpmRange.first)); add(JsonPrimitive(rpmRange.last)) })
                    put("colRange", buildJsonArray { add(JsonPrimitive(tpsRange.first)); add(JsonPrimitive(tpsRange.last)) })
                    put("cellUnit", "raw pulse-width units (uncorrected; not physical microseconds)")
                    put("cells", cells)
                },
            )
        }

    private fun errorsToJson(errors: Collection<Error>?): JsonArray = buildJsonArray {
        errors?.forEach { error ->
            add(
                buildJsonObject {
                    put("code", error.code)
                    put("description", error.description)
                    put("type", error.type?.name)
                },
            )
        }
    }
}
