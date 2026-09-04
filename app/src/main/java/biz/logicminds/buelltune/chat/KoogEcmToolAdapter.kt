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

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Adapts [EcmTools]' six read-only suspend methods to native Koog [SimpleTool]s
 * (KTD2 - no MCP layer, since the only consumer is this same process). This
 * file - and only this file - imports both `ai.koog.*` and
 * [EcmTools]/[ToolResult]: every other Koog-facing file talks to the chat
 * agent through [ecmToolRegistry] instead.
 *
 * Each tool reuses its [EcmTools] method's `read_*`/`get_*`/`list_*` name
 * verbatim (R3) so the model's own tool-call vocabulary matches the names
 * `list_live_variables`' result payload already uses. Every call is wrapped
 * in a 15s [withTimeoutOrNull] (R12): Koog itself has no per-tool-call
 * timeout, so a hung ECM transaction would otherwise stall the whole
 * agentic loop.
 */
private const val TOOL_CALL_TIMEOUT_MS = 15_000L

private val toolResultJson = Json { encodeDefaults = true }

/** Runs [call], applying the shared 15s timeout (R12) and JSON-encoding the resulting [ToolResult] for the model. */
private suspend fun runToolCall(call: suspend () -> ToolResult): String {
    val result = withTimeoutOrNull(TOOL_CALL_TIMEOUT_MS) { call() }
        ?: ToolResult.Error("Tool call timed out after ${TOOL_CALL_TIMEOUT_MS / 1000}s.")
    return toolResultJson.encodeToString(ToolResult.serializer(), result)
}

/** Shared empty-args shape for the three tools that take no parameters. */
@Serializable
data class NoArgs(
    @property:LLMDescription("This tool takes no parameters; call it with an empty object.")
    val unused: Boolean = true,
)

@Serializable
data class ReadLiveDataArgs(
    @property:LLMDescription("Runtime variable names to read, exactly as returned by list_live_variables.")
    val variables: List<String>,
)

@Serializable
data class GetEepromParameterArgs(
    @property:LLMDescription("EEPROM parameter name to read.")
    val name: String,
)

@Serializable
data class GetFuelMapRegionArgs(
    @property:LLMDescription("Cylinder bank: 'front' or 'rear'.")
    val cylinder: String,
    @property:LLMDescription("0-indexed inclusive first RPM row of the fuel table (not a physical RPM value).")
    val rpmStart: Int,
    @property:LLMDescription("0-indexed inclusive last RPM row of the fuel table (not a physical RPM value).")
    val rpmEnd: Int,
    @property:LLMDescription("0-indexed inclusive first TPS column of the fuel table (not a physical TPS value).")
    val tpsStart: Int,
    @property:LLMDescription("0-indexed inclusive last TPS column of the fuel table (not a physical TPS value).")
    val tpsEnd: Int,
)

class GetEcmInfoTool(private val tools: EcmTools) : SimpleTool<NoArgs>(
    typeToken<NoArgs>(),
    "get_ecm_info",
    "Returns identity info (ECM id, firmware version, protocol, transport) for the currently connected ECM.",
) {
    override suspend fun execute(args: NoArgs): String = runToolCall { tools.getEcmInfo() }
}

class ListLiveVariablesTool(private val tools: EcmTools) : SimpleTool<NoArgs>(
    typeToken<NoArgs>(),
    "list_live_variables",
    "Lists every runtime live-data variable name (with its unit) the connected ECM supports. " +
        "Call this before read_live_data to discover valid names.",
) {
    override suspend fun execute(args: NoArgs): String = runToolCall { tools.listLiveVariables() }
}

class ReadLiveDataTool(private val tools: EcmTools) : SimpleTool<ReadLiveDataArgs>(
    typeToken<ReadLiveDataArgs>(),
    "read_live_data",
    "Reads current values for an explicit list of runtime live-data variable names. " +
        "Use list_live_variables first to discover valid names; never guess them.",
) {
    override suspend fun execute(args: ReadLiveDataArgs): String = runToolCall { tools.readLiveData(args.variables) }
}

class ReadErrorCodesTool(private val tools: EcmTools) : SimpleTool<NoArgs>(
    typeToken<NoArgs>(),
    "read_error_codes",
    "Reads both current and stored diagnostic error codes from the connected ECM.",
) {
    override suspend fun execute(args: NoArgs): String = runToolCall { tools.readErrorCodes() }
}

class GetEepromParameterTool(private val tools: EcmTools) : SimpleTool<GetEepromParameterArgs>(
    typeToken<GetEepromParameterArgs>(),
    "get_eeprom_parameter",
    "Reads a single named EEPROM configuration parameter's scaled value and unit. Requires the " +
        "rider to have already fetched EEPROM data on this connection.",
) {
    override suspend fun execute(args: GetEepromParameterArgs): String =
        runToolCall { tools.getEepromParameter(args.name) }
}

class GetFuelMapRegionTool(private val tools: EcmTools) : SimpleTool<GetFuelMapRegionArgs>(
    typeToken<GetFuelMapRegionArgs>(),
    "get_fuel_map_region",
    "Reads a rectangular region of raw pulse-width cells from the front or rear fuel table, " +
        "addressed by 0-indexed RPM row / TPS column ranges (not physical RPM/TPS values). " +
        "Requires the rider to have already fetched EEPROM data on this connection.",
) {
    override suspend fun execute(args: GetFuelMapRegionArgs): String = runToolCall {
        tools.getFuelMapRegion(args.cylinder, args.rpmStart..args.rpmEnd, args.tpsStart..args.tpsEnd)
    }
}

/**
 * The chat feature's entire read-only safety boundary artifact (R1, R8,
 * KD2): this registry contains exactly [EcmTools]' six read-only tools and
 * nothing else, ever. No write/reset/active-test capability is - or may
 * become - reachable from here.
 */
fun ecmToolRegistry(tools: EcmTools): ToolRegistry = ToolRegistry {
    tool(GetEcmInfoTool(tools))
    tool(ListLiveVariablesTool(tools))
    tool(ReadLiveDataTool(tools))
    tool(ReadErrorCodesTool(tools))
    tool(GetEepromParameterTool(tools))
    tool(GetFuelMapRegionTool(tools))
}
