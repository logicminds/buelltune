# Graph Report - chat-feature  (2026-09-04)

## Corpus Check
- 176 files · ~155,614 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1922 nodes · 4259 edges · 118 communities (78 shown, 35 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 305 edges (avg confidence: 0.83)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- AppPreferences & Log Fragment
- EcmService Foreground Polling
- EEPROM Paginated Storage
- ECM Facade Core
- Vendored BLE GATT Socket
- PDU Wire Protocol & Vars
- Connection Status UI State
- Legacy Android Fragment APIs
- ecmsim EEPROM & CLI
- PDU Framing I/O
- EEPROM Fragment Content Provider
- ChatFragment UI
- DataChannel Fragment UI
- Vendored BLE Devices Fragment
- Database Variable Provider
- Bits/Names Room DAOs
- PollRecordLoop Coroutine
- USB Serial Transport
- ChatAgent Fake Transports
- Manifest & BitSet Compliance Tests
- ChatAgentFactory Model Catalog
- BLE Transport Tests
- ChatRepository & Fakes
- Bit/BitSet Model
- Legacy Preferences Screen
- EEPROM SAF Instrumented Tests
- EcmTools Fake-Transport Tests
- ProgressDialogTask AsyncTask Base
- EEPROM Fragment Menu/Intent
- VariableProvider Tests
- EcmTransport Factory
- LLM Settings & OAuth
- JDBC EEPROM/BitSet Providers
- KoogEcmToolAdapter Bridge
- Service Test Support Harness
- EcmTransport Contract Suite
- EcmTools Chat Tool Layer
- Splash/About Activities
- BLE Transport Implementation
- TCP Transport (ecmsim)
- CellEditor Dialog Fragment
- JDBC Variable Provider Rows
- BLE Output Stream
- PollRecordLoop Tests
- Chat Read-Only Tools Plan
- AppContainer DI & Chat DB
- Visual Rebrand Theme/Screens Plan
- EcmSim Connection-Loss Tests
- Kotlin Foundation Compliance Plan
- Chat Message/Conversation Adapters
- EcmDefinitionsDatabase Row Tests
- Chat Room DAOs
- RecordingFormat Tests
- Branding & Testing Docs
- TestChatAgent LLM Fakes
- EcmTools Integration Test
- Transport Contract Test Harnesses
- USB Serial Transport Tests
- EcmDefinitionsProvider (Room)
- Developer Guide Sections
- Release Prep Script
- ActiveTests Fragment
- Bluetooth Classic Transport
- PDU/Byte Semantics Tests
- BuellTune Banner SVG Design
- MainActivity Bluetooth Permission Test
- Shared Test Utils & Log Fixtures
- VariableProvider Bitfield/Scalar Names
- TroubleCode Fragment Connect UI
- ChatAgent Roles & Messaging
- Variable DataType Enum
- ecmsim Maven Wrapper Script
- AGENTS.md & CI Workflows
- Beads Issue Tracker Setup
- ChatAgentFactory Provider Tests
- FakeVariableProvider Test Double
- Rtoffsets DAO
- NoOpVariableProvider Stub
- Hexdump Utility Tests
- FakeBitSetProvider & Constants
- Eeprom DAO
- Polled Byte Queue InputStream
- BuellTune Icon SVG Design
- BLE Transport Error-Path Tests
- AGENTS.md Architecture Overview
- Vendored BLE ScanState Enum
- BuellTune App Logo Design
- Splash Banner Design
- ecmsim Asset Database Loader
- ColorMap Utility
- gradlew Wrapper Script
- BuellTune Brand Banner PNG
- AGENTS.md DB/Variable Overview
- ChatAgent System Prompt
- Launcher Icons (mdpi/xxxhdpi)
- EcmSim Integration Suite Aggregator
- Beads post-checkout Hook
- Beads post-merge Hook
- Beads pre-commit Hook
- Beads pre-push Hook
- Beads prepare-commit-msg Hook
- mysql2sqlite Conversion Script
- ecmsim.sh Launch Script
- Bin2MslConverter
- Launcher Icon (hdpi)
- Launcher Icon (xhdpi)
- Launcher Icon (xxhdpi)
- Changelog v0.1.0 Entry
- User Guide: Connecting to ECM
- User Guide: EEPROM Files
- User Guide: Screen-by-Screen
- User Guide: Troubleshooting
- ecmsim Maven POM

## God Nodes (most connected - your core abstractions)
1. `ECM` - 85 edges
2. `Variable` - 56 edges
3. `PDU` - 52 edges
4. `MainActivity` - 42 edges
5. `AppPreferences` - 36 edges
6. `ChatFragment` - 36 edges
7. `EcmTransport` - 35 edges
8. `SerialSocket` - 34 edges
9. `PollRecordLoop` - 33 edges
10. `ConnectionState` - 32 edges

## Surprising Connections (you probably didn't know these)
- `AGENTS.md Beads Integration Block` --semantically_similar_to--> `CLAUDE.md Beads Integration Block`  [INFERRED] [semantically similar]
  AGENTS.md → CLAUDE.md
- `AGENTS.md Beads Integration Block` --references--> `Beads (bd) Issue Tracker`  [INFERRED]
  AGENTS.md → .beads/README.md
- `CLAUDE.md Beads Integration Block` --references--> `Beads (bd) Issue Tracker`  [INFERRED]
  CLAUDE.md → .beads/README.md
- `U6: Port ECM/EEPROM to Kotlin plus AppContainer` --conceptually_related_to--> `ECM Facade`  [INFERRED]
  docs/plans/2026-08-30-001-refactor-kotlin-foundation-compliance-plan.md → AGENTS.md
- `Release v0.1.1` --conceptually_related_to--> `Visual Rebrand - App Icon and Docs Naming Pass Plan`  [INFERRED]
  CHANGELOG.md → docs/plans/2026-09-01-001-feat-visual-rebrand-icon-docs-plan.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Beads Issue Tracking Ecosystem** — agents_beads_integration, claude_beads_integration, beads_readme_beads, agents_skills_beads_skill_beads_skill [INFERRED 0.85]
- **Read-Only ECM Chat Architecture** — docs_plans_2026_09_03_001_feat_chat_readonly_ecm_tools_plan_ecmtools_facade, docs_plans_2026_09_03_001_feat_chat_readonly_ecm_tools_plan_koog_tool_adapter, docs_plans_2026_09_03_001_feat_chat_readonly_ecm_tools_plan_chat_agent_loop, docs_developer_guide_chat_architecture [EXTRACTED 1.00]
- **BuellTune Visual Rebrand Pipeline** — docs_plans_2026_08_30_001_refactor_kotlin_foundation_compliance_plan_plan, docs_plans_2026_09_01_001_feat_visual_rebrand_icon_docs_plan_plan, docs_plans_2026_09_01_002_feat_visual_rebrand_theme_screens_plan_plan, docs_branding_readme_digital_wing_identity [EXTRACTED 1.00]
- **BuellTune Splash Branding Composition (logo mark + wordmark + tagline rendered full-screen for SplashActivity)** — app_src_main_res_drawable_nodpi_splash_banner, app_src_main_res_drawable_nodpi_splash_banner_logo_mark, app_src_main_res_drawable_nodpi_splash_banner_wordmark, app_src_main_res_drawable_nodpi_splash_banner_tagline [EXTRACTED 1.00]
- **BuellTune Logo Visual Identity Composition** — app_src_main_res_drawable_buelltune_logo_ascendingbarchartmotif, app_src_main_res_drawable_buelltune_logo_pistonwedgeglyph, app_src_main_res_drawable_buelltune_logo_neontrendline [INFERRED 0.80]
- **Launcher Icon Density Variants** — app_src_main_res_mipmap_xxxhdpi_ic_launcher_ic_launcher_xxxhdpi, app_src_main_res_mipmap_mdpi_ic_launcher_ic_launcher_mdpi [INFERRED 0.90]
- **Digital Wing Logo Mark Composition** — docs_branding_buelltune_banner_svg_pegasus_crest, docs_branding_buelltune_banner_svg_equalizer_feathers, docs_branding_buelltune_banner_svg_telemetry_node_overlay [EXTRACTED 1.00]
- **Telemetry Tech Branding Motif** — docs_branding_buelltune_banner_svg_wordmark, docs_branding_buelltune_banner_svg_tagline, docs_branding_buelltune_banner_svg_cyan_grad [INFERRED 0.75]
- **Brand visual identity: flame + metal + telemetry gradients composing the wing icon** — docs_branding_icon_logomark, docs_branding_icon_flamegrad, docs_branding_icon_metalgrad, docs_branding_icon_digitalnodecircuit [EXTRACTED 1.00]

## Communities (118 total, 35 thin omitted)

### Community 0 - "AppPreferences & Log Fragment"
Cohesion: 0.06
Nodes (13): AppPreferences, Activity, Context, Interval, Override, StopTask, Bin2MslConverter, Observable (+5 more)

### Community 1 - "EcmService Foreground Polling"
Cohesion: 0.05
Nodes (24): EcmServiceBroadcastInstrumentedTest, BroadcastReceiver, FakeTcpEcmServer, AutoCloseable, Context, Intent, PDU, Socket (+16 more)

### Community 2 - "EEPROM Paginated Storage"
Cohesion: 0.07
Nodes (14): EepromPageRow, EEPROM, ByteArray, Context, Page, Page, FakeEcmServer, AutoCloseable (+6 more)

### Community 3 - "ECM Facade Core"
Cohesion: 0.06
Nodes (16): ECM, ByteArray, Context, EEPROM, Page, PDU, Protocol, FACTORY_RACE (+8 more)

### Community 4 - "Vendored BLE GATT Socket"
Cohesion: 0.09
Nodes (15): android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCallback, android.bluetooth.BluetoothGattCharacteristic, android.bluetooth.BluetoothGattDescriptor, android.bluetooth.BluetoothGattService, android.content.IntentFilter, SerialListener, Cc245XDelegate (+7 more)

### Community 5 - "PDU Wire Protocol & Vars"
Cohesion: 0.06
Nodes (20): Function, Active_Intake, ClearCodes, Exh_Valve, Fan, FrontCoil, FrontInj, FuelPump (+12 more)

### Community 6 - "Connection Status UI State"
Cohesion: 0.07
Nodes (33): ConnectionStatusScreenTest, FakeEcmLiveDataSource, StateFlow, BuellTuneDebugActivity, Bundle, BuellTuneDestinations, BuellTuneNavHost(), BuellTuneTheme() (+25 more)

### Community 7 - "Legacy Android Fragment APIs"
Cohesion: 0.11
Nodes (23): android.app.Fragment, android.content.BroadcastReceiver, android.content.DialogInterface, android.content.res.ColorStateList, android.content.ServiceConnection, android.os.IBinder, android.view.MenuItem, android.view.View.OnClickListener (+15 more)

### Community 8 - "ecmsim EEPROM & CLI"
Cohesion: 0.07
Nodes (14): ch.qos.logback.classic.Logger, DataInputStream, org.json.JSONObject, org.slf4j.Logger, picocli.CommandLine.Command, EEPROM, Override, Page (+6 more)

### Community 9 - "PDU Framing I/O"
Cohesion: 0.08
Nodes (12): ByteArray, PDU, PduFraming, EcmSimProcess, EcmSimProtocolIntegrationTest, Connection, EcmSimRule, AutoCloseable (+4 more)

### Community 10 - "EEPROM Fragment Content Provider"
Cohesion: 0.10
Nodes (14): android.content.ContentProvider, android.content.ContentValues, android.content.Context, android.database.Cursor, android.net.Uri, android.os.ParcelFileDescriptor, android.widget.BaseAdapter, android.widget.SectionIndexer (+6 more)

### Community 11 - "ChatFragment UI"
Cohesion: 0.12
Nodes (12): ChatFragment, Bundle, Button, Fragment, Job, LayoutInflater, TextView, View (+4 more)

### Community 12 - "DataChannel Fragment UI"
Cohesion: 0.15
Nodes (13): android.os.Bundle, android.view.LayoutInflater, android.view.View, android.view.ViewGroup, android.widget.ToggleButton, androidx.annotation.NonNull, androidx.annotation.Nullable, DataChannelAdapter (+5 more)

### Community 13 - "Vendored BLE Devices Fragment"
Cohesion: 0.11
Nodes (14): android.app.ListFragment, android.bluetooth.BluetoothAdapter, android.bluetooth.BluetoothDevice, android.widget.ArrayAdapter, android.widget.ListView, android.widget.Spinner, Override, MainFragment (+6 more)

### Community 14 - "Database Variable Provider"
Cohesion: 0.10
Nodes (3): DatabaseVariableProvider, Variable, StubVariableProvider

### Community 15 - "Bits/Names Room DAOs"
Cohesion: 0.08
Nodes (9): AdxbitsDao, BitNamesRow, BitsDao, EeoffsetsDao, PagesDao, AdxbitsEntity, BitsEntity, EeoffsetsEntity (+1 more)

### Community 16 - "PollRecordLoop Coroutine"
Cohesion: 0.15
Nodes (9): bigEndianInt(), Clock, ByteArray, Flow, Job, StateFlow, PollRecordLoop, RecordingSink (+1 more)

### Community 17 - "USB Serial Transport"
Cohesion: 0.12
Nodes (14): Data, Error, ByteArray, CompletableDeferred, Flow, OutputStream, PDU, StateFlow (+6 more)

### Community 18 - "ChatAgent Fake Transports"
Cohesion: 0.17
Nodes (6): HangingEcmTransport, InstantEcmTransport, PDU, StateFlow, ScriptedPromptExecutor, TestChatAgent

### Community 19 - "Manifest & BitSet Compliance Tests"
Cohesion: 0.16
Nodes (10): androidx.test.ext.junit.runners.AndroidJUnit4, ManifestComplianceInstrumentedTest, TestBitSetProvider, TestECM, TestEEPROM, biz.logicminds.buelltune.AppContainer, biz.logicminds.buelltune.Error.ErrorType, org.junit.Before (+2 more)

### Community 20 - "ChatAgentFactory Model Catalog"
Cohesion: 0.15
Nodes (14): ChatAgentFactory, LLModel, ProviderCredentials, ProviderId, ANTHROPIC, DEEPSEEK, GOOGLE, KIMI (+6 more)

### Community 21 - "BLE Transport Tests"
Cohesion: 0.12
Nodes (11): BleSerialSocket, BleSerialSocket, BleSerialSocket, fakeBleSerialSocket(), BleSerialSocket, BleSerialSocket, ByteArray, CompletableDeferred (+3 more)

### Community 22 - "ChatRepository & Fakes"
Cohesion: 0.16
Nodes (10): FakeDefinitionsProvider, TestChatRepository, ChatAgentResult, ConversationTurn, asChatSender(), ChatRepository, ChatSender, Flow (+2 more)

### Community 23 - "Bit/BitSet Model"
Cohesion: 0.11
Nodes (9): Bit, ByteArray, BitSet, ByteArray, Type, DDFI1, DDFI2, DDFI3 (+1 more)

### Community 24 - "Legacy Preferences Screen"
Cohesion: 0.13
Nodes (15): android.preference.Preference, android.preference.Preference.OnPreferenceChangeListener, android.preference.PreferenceActivity, android.preference.PreferenceFragment, android.preference.PreferenceGroup, android.widget.Button, PrefsActivity, Override (+7 more)

### Community 25 - "EEPROM SAF Instrumented Tests"
Cohesion: 0.17
Nodes (7): Activity, EEPROMFragmentSafInstrumentedTest, ActivityScenario, Context, Intent, Uri, EcmSimFixtures

### Community 26 - "EcmTools Fake-Transport Tests"
Cohesion: 0.19
Nodes (7): JdbcEcmDefinitionsProvider, FakeEcmTransport, Connection, EEPROM, PDU, StateFlow, TestEcmTools

### Community 27 - "ProgressDialogTask AsyncTask Base"
Cohesion: 0.13
Nodes (10): android.app.ProgressDialog, android.content.DialogInterface.OnCancelListener, android.os.AsyncTask, Override, ProgressDialogTask, Activity, Context, Menu (+2 more)

### Community 28 - "EEPROM Fragment Menu/Intent"
Cohesion: 0.13
Nodes (10): android.content.Intent, android.view.ContextMenu, android.view.Menu, android.view.MenuInflater, Intent, Override, EEPROMFragment, Intent (+2 more)

### Community 30 - "EcmTransport Factory"
Cohesion: 0.14
Nodes (10): EcmTransport, PDU, StateFlow, BluetoothDevice, ByteArray, Context, UsbSerialConnection, UsbSerialPort (+2 more)

### Community 31 - "LLM Settings & OAuth"
Cohesion: 0.15
Nodes (9): AppCompatActivity, Bundle, Intent, LlmPreferenceFragment, LlmSettingsActivity, ByteArray, OpenRouterOAuth, PreferenceFragmentCompat (+1 more)

### Community 32 - "JDBC EEPROM/BitSet Providers"
Cohesion: 0.17
Nodes (10): BitSetProvider, DataSource, EEPROM, RUNTIME_DATA, BitSetRow, DatabaseBitSetProvider, Units, JdbcBitSetProvider (+2 more)

### Community 33 - "KoogEcmToolAdapter Bridge"
Cohesion: 0.24
Nodes (14): ecmToolRegistry(), GetEcmInfoTool, GetEepromParameterArgs, GetEepromParameterTool, GetFuelMapRegionArgs, GetFuelMapRegionTool, ListLiveVariablesTool, NoArgs (+6 more)

### Community 34 - "Service Test Support Harness"
Cohesion: 0.11
Nodes (9): Fail, FakeEcmTransport, FakeOutcome, PDU, StateFlow, newEcm(), NoOpBitSetProvider, NoOpVariableProvider (+1 more)

### Community 35 - "EcmTransport Contract Suite"
Cohesion: 0.22
Nodes (7): EcmTransportContractTest, CompletableDeferred, ackWithBytes(), FakePduServer, AutoCloseable, ByteArray, PDU

### Community 36 - "EcmTools Chat Tool Layer"
Cohesion: 0.16
Nodes (9): EcmTools, EepromNotRead, Error, NotConnected, Ok, ToolResult, Connection, IntRange (+1 more)

### Community 37 - "Splash/About Activities"
Cohesion: 0.14
Nodes (7): android.app.Activity, android.os.Handler, AboutActivity, Override, Override, SplashActivity, FetchTask

### Community 38 - "BLE Transport Implementation"
Cohesion: 0.18
Nodes (9): BleTransport, SerialListener, Connect, ConnectError, IoError, BleSerialSocket, PDU, Read (+1 more)

### Community 39 - "TCP Transport (ecmsim)"
Cohesion: 0.17
Nodes (8): PDU, Socket, StateFlow, TcpTransport, CompletableDeferred, TcpHarness, TcpTransportContractTest, TcpTransportTest

### Community 40 - "CellEditor Dialog Fragment"
Cohesion: 0.19
Nodes (10): android.annotation.SuppressLint, android.app.Dialog, android.app.DialogFragment, android.content.DialogInterface.OnClickListener, android.view.KeyEvent, android.view.View.OnKeyListener, android.widget.EditText, CellEditorDialogFragment (+2 more)

### Community 41 - "JDBC Variable Provider Rows"
Cohesion: 0.24
Nodes (3): EeVariableRow, RtVariableRow, JdbcVariableProvider

### Community 42 - "BLE Output Stream"
Cohesion: 0.17
Nodes (9): BleOutputStream, BleSerialSocket, ByteArray, Flow, Job, OutputStream, SerialListener, StateFlow (+1 more)

### Community 43 - "PollRecordLoop Tests"
Cohesion: 0.25
Nodes (6): ByteArray, PDU, PollRecordLoopTest, RecordingSinkSpy, ackWithBytes(), ByteArray

### Community 44 - "Chat Read-Only Tools Plan"
Cohesion: 0.20
Nodes (17): ChatAgent Agentic Loop, Chat Architecture and Tool Layer (Section 16), ChatFragment Dual-FragmentManager Hosting, Conversation Persistence and Resume-Replay Rule, EcmTools, KoogEcmToolAdapter, Suggestion Cards, ChatAgent Agentic Loop (U5) (+9 more)

### Community 45 - "AppContainer DI & Chat DB"
Cohesion: 0.18
Nodes (8): android.app.Application, AppContainer, Context, ChatDatabase, Context, RoomDatabase, EcmDroidApp, Override

### Community 46 - "Visual Rebrand Theme/Screens Plan"
Cohesion: 0.15
Nodes (16): About Screen (about.html), EcmDroid Fork Attribution, Release v0.1.1, Android Asset Derivation Process, Brand Palette, KD1: New Artwork Original, Not Derived, KD2: Full Adaptive Icon, Visual Rebrand - App Icon and Docs Naming Pass Plan (+8 more)

### Community 47 - "EcmSim Connection-Loss Tests"
Cohesion: 0.21
Nodes (7): Recording, RecordingState, Stopped, CapturingRecordingSink, EcmSimConnectionLossIntegrationTest, ByteArray, Connection

### Community 48 - "Kotlin Foundation Compliance Plan"
Cohesion: 0.18
Nodes (16): AppContainer DI Composition Root Pattern (KTD5), KD1: Rebrand as Hard Fork, KD8: Big-Bang Package Rename, BuellTune Kotlin Foundation and Compliance Plan, U1: Big-Bang Rename to biz.logicminds.buelltune, U2: Play Store Compliance, U3: Enable Kotlin, KSP, Compose, U4: Port Pure-Java Domain Classes to Kotlin (+8 more)

### Community 49 - "Chat Message/Conversation Adapters"
Cohesion: 0.24
Nodes (7): Adapter, ChatMessageAdapter, ConversationAdapter, ViewGroup, ViewHolder, LinearLayout, Markwon

### Community 50 - "EcmDefinitionsDatabase Row Tests"
Cohesion: 0.16
Nodes (6): TestEcmDefinitionsDatabase, NamesDao, EcmDefinitionsDatabase, Context, RoomDatabase, NamesEntity

### Community 51 - "Chat Room DAOs"
Cohesion: 0.20
Nodes (6): ChatMessageDao, ConversationDao, ConversationWithPreview, Flow, ChatMessageEntity, ConversationEntity

### Community 52 - "RecordingFormat Tests"
Cohesion: 0.26
Nodes (6): CapturingSink, FakeClock, ByteArray, RecordingFormatTest, FixedIdDefinitionsProvider, CoroutineScope

### Community 53 - "Branding & Testing Docs"
Cohesion: 0.18
Nodes (13): ecmsim-backed JVM Integration Suite, buelltune-banner.svg, Digital Wing Brand Identity, icon.svg, Testing (Section 13), ecmsim-backed JVM Integration Suite (R16/R17), U2: BuellTune Product-Naming Docs Pass, User Guide (+5 more)

### Community 54 - "TestChatAgent LLM Fakes"
Cohesion: 0.24
Nodes (10): Connection, EEPROM, Flow, JsonObject, LLModel, Message, ModerationResult, Prompt (+2 more)

### Community 56 - "Transport Contract Test Harnesses"
Cohesion: 0.21
Nodes (8): BleHarness, BleTransportContractTest, BluetoothClassicTransportContractTest, BtHarness, BluetoothSocket, CompletableDeferred, mockConnectedSocket(), TransportHarness

### Community 57 - "USB Serial Transport Tests"
Cohesion: 0.21
Nodes (8): fakeUsbConnection(), UsbSerialConnection, ByteArray, CompletableDeferred, SerialInputOutputManager, UsbSerialConnection, UsbHarness, UsbSerialTransportContractTest

### Community 58 - "EcmDefinitionsProvider (Room)"
Cohesion: 0.18
Nodes (5): EcmDefinitionsProvider, EEPROM, RoomEcmDefinitionsProvider, EEPROM, NoOpDefinitionsProvider

### Community 59 - "Developer Guide Sections"
Cohesion: 0.21
Nodes (12): Async Task Pattern (Section 11), Background Service and Threading (Section 9), Binary Log Format and MSL Conversion (Section 12), Developer Guide, Bundled ECM-Definitions Database (Section 8), ECM Central Facade (Section 6), EEPROM/Variable/BitSet Data Model (Section 7), PDU Wire Protocol (Section 4) (+4 more)

### Community 60 - "Release Prep Script"
Cohesion: 0.29
Nodes (12): build_changelog_section(), bump_semver(), commit_range_subjects(), find_since_ref(), insert_changelog_section(), main(), [(short_sha, subject), ...] oldest first, excluding merges and prior release…, Previous release tag, else the commit that added CHANGELOG.md, else None. (+4 more)

### Community 61 - "ActiveTests Fragment"
Cohesion: 0.27
Nodes (3): ActiveTestsFragment, FunctionTask, Override

### Community 62 - "Bluetooth Classic Transport"
Cohesion: 0.27
Nodes (5): BluetoothClassicTransport, BluetoothSocket, PDU, StateFlow, BluetoothClassicTransportTest

### Community 63 - "PDU/Byte Semantics Tests"
Cohesion: 0.23
Nodes (4): ByteArray, TestByteSemantics, TestPDU, org.junit.runners.JUnit4

### Community 64 - "BuellTune Banner SVG Design"
Cohesion: 0.21
Nodes (12): BuellTune Banner (SVG), Background Gradient (bg-grad), Telemetry Cyan Glow Filter (cyan-glow), Telemetry Cyan Gradient (cyan-grad), Digital Wing Logo Mark, Wing Feathers as Power/RPM Equalizer Bars, Flame Gradient — Buell Racing Orange (flame-grad), Metallic Wing Gradient (metal-grad) (+4 more)

### Community 65 - "MainActivity Bluetooth Permission Test"
Cohesion: 0.36
Nodes (3): ActivityScenario, Context, MainActivityBluetoothPermissionInstrumentedTest

### Community 69 - "ChatAgent Roles & Messaging"
Cohesion: 0.22
Nodes (7): ChatAgent, Role, ASSISTANT, USER, extractSuggestion(), SuggestionCard, PromptExecutor

### Community 70 - "Variable DataType Enum"
Cohesion: 0.20
Nodes (10): DataType, ARRAY, AXIS, BITFIELD, BITS, MAP, SCALAR, STRING (+2 more)

### Community 71 - "ecmsim Maven Wrapper Script"
Cohesion: 0.38
Nodes (8): mvnw script, clean(), die(), exec_maven(), hash_string(), set_java_home(), trim(), verbose()

### Community 72 - "AGENTS.md & CI Workflows"
Cohesion: 0.22
Nodes (8): AGENTS.md Repository Guidelines, graphify Knowledge Graph, CI Workflow, Instrumented Tests CI Job, create-release Workflow, scripts/prepare_release.py, Release Workflow, tag-release Workflow

### Community 73 - "Beads Issue Tracker Setup"
Cohesion: 0.25
Nodes (9): AGENTS.md Beads Codex Setup Block, AGENTS.md Beads Integration Block, Beads OpenAI Agent Interface, bd CLI, Beads Skill Documentation, Beads Repository Configuration, Beads (bd) Issue Tracker, Dolt (+1 more)

### Community 79 - "FakeBitSetProvider & Constants"
Cohesion: 0.33
Nodes (4): FakeBitSetProvider, Constants, Variables, Pattern

### Community 81 - "Polled Byte Queue InputStream"
Cohesion: 0.38
Nodes (3): ByteArray, InputStream, PolledByteQueueInputStream

### Community 82 - "BuellTune Icon SVG Design"
Cohesion: 0.48
Nodes (7): BuellTune App Icon (Digital Wing / Pegasus Mark), cyan-glow: Gaussian blur glow filter for telemetry accents, cyan-grad: Telemetry Cyan gradient (#00E5FF to #0088FF), Digital Node Circuit Overlay: dashed/solid connector lines and glowing nodes tracing the wing silhouette, flame-grad: Buell Racing Orange gradient (#FF5500 to #FF2A00), logo-mark group: metallic Pegasus crest with flame-gradient wing feathers and cyan digital circuit overlay, metal-grad: Metallic Wing gradient (#F4F5F7 to #4A505A)

### Community 84 - "AGENTS.md Architecture Overview"
Cohesion: 0.40
Nodes (5): BuellTune App, ECM Facade, EcmService, EEPROM Data Model, PDU Wire Protocol

### Community 86 - "Vendored BLE ScanState Enum"
Cohesion: 0.40
Nodes (5): ScanState, DISCOVERY, DISCOVERY_FINISHED, LE_SCAN, NONE

### Community 87 - "BuellTune App Logo Design"
Cohesion: 0.60
Nodes (5): BuellTune App Branding Identity, Ascending Bar Chart Motif, BuellTune Logo, Neon Cyan Trend Line, Piston/Wedge Glyph

### Community 88 - "Splash Banner Design"
Cohesion: 0.50
Nodes (5): Splash Banner (Full-Screen Splash Background Asset), BuellTune Ascending Bar-Chart Logo Mark (gray-to-orange bars with cyan dotted trend line), ECU DIAGNOSTICS & CALIBRATION Tagline, BUELL TUNE Wordmark (white BUELL + blue-glow TUNE), activity_splash.xml (SplashActivity FrameLayout)

### Community 91 - "gradlew Wrapper Script"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 92 - "BuellTune Brand Banner PNG"
Cohesion: 1.00
Nodes (3): BuellTune Brand Banner (docs/branding/buelltune-banner.png), Tagline: "ECU Diagnostics & Calibration", BuellTune Visual Identity (bar-chart/bolt mark, orange-to-blue gradient, dark theme)

## Ambiguous Edges - Review These
- `User Guide` → `Privacy Policy`  [AMBIGUOUS]
  privacy-policy.md · relation: conceptually_related_to

## Knowledge Gaps
- **114 isolated node(s):** `BuellTuneDestinations`, `Variables`, `RUNTIME_DATA`, `EEPROM`, `DDFI1` (+109 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 372 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **35 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `User Guide` and `Privacy Policy`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `ECM` connect `ECM Facade Core` to `AppPreferences & Log Fragment`, `EcmService Foreground Polling`, `EEPROM Paginated Storage`, `PDU Wire Protocol & Vars`, `Connection Status UI State`, `PDU Framing I/O`, `DataChannel Fragment UI`, `Database Variable Provider`, `PollRecordLoop Coroutine`, `ChatAgent Fake Transports`, `Manifest & BitSet Compliance Tests`, `ChatAgentFactory Model Catalog`, `ChatRepository & Fakes`, `Bit/BitSet Model`, `EEPROM SAF Instrumented Tests`, `EcmTools Fake-Transport Tests`, `EcmTransport Factory`, `JDBC EEPROM/BitSet Providers`, `Service Test Support Harness`, `EcmTools Chat Tool Layer`, `Splash/About Activities`, `PollRecordLoop Tests`, `AppContainer DI & Chat DB`, `EcmSim Connection-Loss Tests`, `RecordingFormat Tests`, `TestChatAgent LLM Fakes`, `EcmTools Integration Test`, `VariableProvider Bitfield/Scalar Names`, `TroubleCode Fragment Connect UI`?**
  _High betweenness centrality (0.264) - this node is a cross-community bridge._
- **Why does `PDU` connect `PDU Wire Protocol & Vars` to `EcmService Foreground Polling`, `Service Test Support Harness`, `EcmTransport Contract Suite`, `TCP Transport (ecmsim)`, `PDU Framing I/O`, `BLE Output Stream`, `PollRecordLoop Tests`, `USB Serial Transport`, `RecordingFormat Tests`, `BLE Transport Tests`, `TestChatAgent LLM Fakes`, `EcmTransport Factory`, `USB Serial Transport Tests`, `VariableProvider Tests`, `Bluetooth Classic Transport`, `PDU/Byte Semantics Tests`?**
  _High betweenness centrality (0.093) - this node is a cross-community bridge._
- **Why does `AppContainer` connect `AppContainer DI & Chat DB` to `JDBC EEPROM/BitSet Providers`, `EcmService Foreground Polling`, `ECM Facade Core`, `EcmTools Chat Tool Layer`, `VariableProvider Bitfield/Scalar Names`, `Connection Status UI State`, `ChatFragment UI`, `Database Variable Provider`, `EcmDefinitionsDatabase Row Tests`, `Manifest & BitSet Compliance Tests`, `ChatAgentFactory Model Catalog`, `ChatRepository & Fakes`, `EcmDefinitionsProvider (Room)`?**
  _High betweenness centrality (0.092) - this node is a cross-community bridge._
- **Are the 3 inferred relationships involving `PDU` (e.g. with `.testCorruptedChecksumRejected()` and `.testGetResponse()`) actually correct?**
  _`PDU` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `BuellTuneDestinations`, `Variables`, `RUNTIME_DATA` to the rest of the system?**
  _114 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `AppPreferences & Log Fragment` be split into smaller, more focused modules?**
  _Cohesion score 0.06012176560121765 - nodes in this community are weakly interconnected._