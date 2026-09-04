# Graph Report - chat-feature  (2026-09-04)

## Corpus Check
- 159 files · ~156,337 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1926 nodes · 4275 edges · 117 communities (81 shown, 32 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 305 edges (avg confidence: 0.83)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `c9deb995`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- AppPreferences
- EcmService
- EEPROM
- ECM
- SerialSocket
- PDU
- ConnectionStatusViewModel
- MainActivity
- EEPROM
- EcmSimProtocolIntegrationTest
- EEPROMAdapter
- ChatFragment
- MainActivity.java
- DevicesFragment
- Variable
- Daos.kt
- PollRecordLoop
- UsbSerialTransport
- TestChatAgent
- org.junit.Test
- ProviderId
- BleTransport
- ChatRepository
- Bit
- SetupFragment.java
- EEPROMFragmentSafInstrumentedTest
- JdbcEcmDefinitionsProvider
- TestEcmProtocol
- EEPROMFragment
- VariableProvider
- TransportFactory
- EcmServiceBroadcastInstrumentedTest.kt
- JdbcEcmDefinitions.kt
- KoogEcmToolAdapter.kt
- ServiceTestSupport.kt
- FakePduServer
- EcmTools
- ProgressDialogTask
- LogFragment
- TcpTransport
- android.view.View
- JdbcVariableProvider
- ConnectionState
- .connectedEcm
- BuellTune Read-Only ECM Chat Plan
- AppContainer
- Visual Rebrand - App Icon and Docs Naming Pass Plan
- EcmSimConnectionLossIntegrationTest.kt
- BuellTune Kotlin Foundation and Compliance Plan
- ChatMessageAdapter
- EcmDefinitionsDatabase
- ChatMessageEntity
- .recordingBytesMatchGoldenDataOutputStreamEncoding
- ecmsim ECM Simulator
- TestChatAgent.kt
- EcmToolsIntegrationTest
- .gatedTransport
- fakeUsbConnection
- EcmDefinitionsProvider
- Developer Guide
- prepare_release.py
- DatabaseVariableProvider
- BluetoothClassicTransport
- Bin2MslConverter
- BuellTune Banner (SVG)
- MainActivityBluetoothPermissionInstrumentedTest
- BitSetProvider
- EcmService.kt
- android.os.Bundle
- ChatAgent.kt
- DataType
- mvnw
- AGENTS.md Repository Guidelines
- Beads (bd) Issue Tracker
- FileOutputStream
- EeoffsetsDao
- RtoffsetsDao
- EcmTransport
- TestUtilsFunctions
- Constants
- EepromDao
- .startRecording
- BuellTune App Icon (Digital Wing / Pegasus Mark)
- .DevicesFragment
- ECM Facade
- .setupPreference
- ScanState
- BuellTune App Branding Identity
- Splash Banner (Full-Screen Splash Background Asset)
- AssetDatabase
- gradlew
- BuellTune Brand Banner (docs/branding/buelltune-banner.png)
- Bundled ECM Definitions Database
- SystemPrompt.kt
- App Launcher Icon (mdpi)
- EcmSimIntegrationSuite.kt
- post-checkout
- post-merge
- pre-commit
- pre-push
- prepare-commit-msg
- mysql2sqlite.sh
- ecmsim.sh
- Bin2MslConverter
- App Launcher Icon (hdpi)
- App Launcher Icon (xhdpi)
- App Launcher Icon (xxhdpi)
- Release v0.1.0
- Connecting to the ECM (Section 5)
- Working with EEPROM Files (Section 8)
- Screen-by-Screen Guide (Section 7)
- Troubleshooting (Section 11)
- org.ecmdroid:ecmsim

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
- `Release v0.1.1` --conceptually_related_to--> `Visual Rebrand - App Icon and Docs Naming Pass Plan`  [INFERRED]
  CHANGELOG.md → docs/plans/2026-09-01-001-feat-visual-rebrand-icon-docs-plan.md
- `U6: Port ECM/EEPROM to Kotlin plus AppContainer` --conceptually_related_to--> `ECM Facade`  [INFERRED]
  docs/plans/2026-08-30-001-refactor-kotlin-foundation-compliance-plan.md → AGENTS.md
- `Privacy Policy` --conceptually_related_to--> `User Guide`  [AMBIGUOUS]
  privacy-policy.md → docs/USER_GUIDE.md
- `AGENTS.md Beads Integration Block` --references--> `Beads (bd) Issue Tracker`  [INFERRED]
  AGENTS.md → .beads/README.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **BuellTune Splash Branding Composition (logo mark + wordmark + tagline rendered full-screen for SplashActivity)** — app_src_main_res_drawable_nodpi_splash_banner, app_src_main_res_drawable_nodpi_splash_banner_logo_mark, app_src_main_res_drawable_nodpi_splash_banner_wordmark, app_src_main_res_drawable_nodpi_splash_banner_tagline [EXTRACTED 1.00]
- **BuellTune Visual Rebrand Pipeline** — docs_plans_2026_08_30_001_refactor_kotlin_foundation_compliance_plan_plan, docs_plans_2026_09_01_001_feat_visual_rebrand_icon_docs_plan_plan, docs_plans_2026_09_01_002_feat_visual_rebrand_theme_screens_plan_plan, docs_branding_readme_digital_wing_identity [EXTRACTED 1.00]
- **Read-Only ECM Chat Architecture** — docs_plans_2026_09_03_001_feat_chat_readonly_ecm_tools_plan_ecmtools_facade, docs_plans_2026_09_03_001_feat_chat_readonly_ecm_tools_plan_koog_tool_adapter, docs_plans_2026_09_03_001_feat_chat_readonly_ecm_tools_plan_chat_agent_loop, docs_developer_guide_chat_architecture [EXTRACTED 1.00]
- **Digital Wing Logo Mark Composition** — docs_branding_buelltune_banner_svg_pegasus_crest, docs_branding_buelltune_banner_svg_equalizer_feathers, docs_branding_buelltune_banner_svg_telemetry_node_overlay [EXTRACTED 1.00]
- **Brand visual identity: flame + metal + telemetry gradients composing the wing icon** — docs_branding_icon_logomark, docs_branding_icon_flamegrad, docs_branding_icon_metalgrad, docs_branding_icon_digitalnodecircuit [EXTRACTED 1.00]
- **Telemetry Tech Branding Motif** — docs_branding_buelltune_banner_svg_wordmark, docs_branding_buelltune_banner_svg_tagline, docs_branding_buelltune_banner_svg_cyan_grad [INFERRED 0.75]
- **BuellTune Logo Visual Identity Composition** — app_src_main_res_drawable_buelltune_logo_ascendingbarchartmotif, app_src_main_res_drawable_buelltune_logo_pistonwedgeglyph, app_src_main_res_drawable_buelltune_logo_neontrendline [INFERRED 0.80]
- **Beads Issue Tracking Ecosystem** — agents_beads_integration, claude_beads_integration, beads_readme_beads, agents_skills_beads_skill_beads_skill [INFERRED 0.85]
- **Launcher Icon Density Variants** — app_src_main_res_mipmap_xxxhdpi_ic_launcher_ic_launcher_xxxhdpi, app_src_main_res_mipmap_mdpi_ic_launcher_ic_launcher_mdpi [INFERRED 0.90]

## Communities (117 total, 32 thin omitted)

### Community 0 - "AppPreferences"
Cohesion: 0.13
Nodes (4): AppPreferences, Activity, Context, SharedPreferences

### Community 2 - "EEPROM"
Cohesion: 0.10
Nodes (5): EEPROM, ByteArray, Context, Page, Page

### Community 3 - "ECM"
Cohesion: 0.07
Nodes (15): ECM, ByteArray, Context, EEPROM, Page, PDU, Protocol, FACTORY_RACE (+7 more)

### Community 4 - "SerialSocket"
Cohesion: 0.09
Nodes (15): android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCallback, android.bluetooth.BluetoothGattCharacteristic, android.bluetooth.BluetoothGattDescriptor, android.bluetooth.BluetoothGattService, android.content.IntentFilter, SerialListener, Cc245XDelegate (+7 more)

### Community 5 - "PDU"
Cohesion: 0.06
Nodes (20): Function, Active_Intake, ClearCodes, Exh_Valve, Fan, FrontCoil, FrontInj, FuelPump (+12 more)

### Community 6 - "ConnectionStatusViewModel"
Cohesion: 0.10
Nodes (20): ConnectionStatusScreenTest, FakeEcmLiveDataSource, StateFlow, BuellTuneDebugActivity, Bundle, BuellTuneDestinations, BuellTuneNavHost(), BuellTuneTheme() (+12 more)

### Community 7 - "MainActivity"
Cohesion: 0.13
Nodes (10): android.bluetooth.BluetoothDevice, android.content.res.ColorStateList, androidx.appcompat.app.AppCompatActivity, ConnectTask, Override, MainActivity, com.google.android.material.floatingactionbutton.FloatingActionButton, com.hoho.android.usbserial.driver.UsbSerialPort (+2 more)

### Community 8 - "EEPROM"
Cohesion: 0.06
Nodes (17): ByteArray, OpenRouterOAuth, ch.qos.logback.classic.Logger, DataInputStream, org.json.JSONObject, org.slf4j.Logger, picocli.CommandLine.Command, EEPROM (+9 more)

### Community 9 - "EcmSimProtocolIntegrationTest"
Cohesion: 0.08
Nodes (12): ByteArray, PDU, PduFraming, EcmSimProcess, EcmSimProtocolIntegrationTest, Connection, EcmSimRule, AutoCloseable (+4 more)

### Community 10 - "EEPROMAdapter"
Cohesion: 0.08
Nodes (16): android.content.ContentProvider, android.content.ContentValues, android.content.Context, android.database.Cursor, android.net.Uri, android.os.ParcelFileDescriptor, android.widget.BaseAdapter, android.widget.SectionIndexer (+8 more)

### Community 11 - "ChatFragment"
Cohesion: 0.14
Nodes (11): ConversationEntity, ChatFragment, Bundle, Fragment, Job, LayoutInflater, View, PromptChipDefinition (+3 more)

### Community 12 - "MainActivity.java"
Cohesion: 0.14
Nodes (28): android.annotation.SuppressLint, android.app.Fragment, android.app.ListFragment, android.bluetooth.BluetoothAdapter, android.content.BroadcastReceiver, android.content.DialogInterface, android.content.ServiceConnection, android.os.IBinder (+20 more)

### Community 13 - "DevicesFragment"
Cohesion: 0.19
Nodes (7): android.view.Menu, android.view.MenuInflater, DevicesFragment, ArrayAdapter, Handler, Override, LeScanCallback

### Community 14 - "Variable"
Cohesion: 0.07
Nodes (4): FakeVariableProvider, Variable, NoOpVariableProvider, StubVariableProvider

### Community 15 - "Daos.kt"
Cohesion: 0.09
Nodes (8): AdxbitsDao, BitsDao, NamesDao, PagesDao, AdxbitsEntity, BitsEntity, NamesEntity, PagesEntity

### Community 16 - "PollRecordLoop"
Cohesion: 0.15
Nodes (9): bigEndianInt(), Clock, ByteArray, Flow, Job, StateFlow, PollRecordLoop, RecordingSink (+1 more)

### Community 17 - "UsbSerialTransport"
Cohesion: 0.07
Nodes (24): AppCompatActivity, Bundle, Intent, LlmPreferenceFragment, LlmSettingsActivity, ByteArray, InputStream, PolledByteQueueInputStream (+16 more)

### Community 18 - "TestChatAgent"
Cohesion: 0.16
Nodes (7): HangingEcmTransport, InstantEcmTransport, PDU, StateFlow, ScriptedPromptExecutor, TestChatAgent, Message

### Community 19 - "org.junit.Test"
Cohesion: 0.12
Nodes (13): androidx.test.ext.junit.runners.AndroidJUnit4, ManifestComplianceInstrumentedTest, TestECM, TestEEPROM, ByteArray, TestBin2Msl, TestByteSemantics, TestPDU (+5 more)

### Community 20 - "ProviderId"
Cohesion: 0.10
Nodes (15): ChatAgentFactory, LLModel, ProviderCredentials, ProviderId, ANTHROPIC, DEEPSEEK, GOOGLE, KIMI (+7 more)

### Community 21 - "BleTransport"
Cohesion: 0.05
Nodes (30): BleOutputStream, BleSerialSocket, BleTransport, SerialListener, Connect, ConnectError, IoError, BleSerialSocket (+22 more)

### Community 22 - "ChatRepository"
Cohesion: 0.18
Nodes (9): TestChatRepository, ChatAgentResult, ConversationTurn, asChatSender(), ChatRepository, ChatSender, Flow, LLModel (+1 more)

### Community 23 - "Bit"
Cohesion: 0.11
Nodes (9): Bit, ByteArray, BitSet, ByteArray, Type, DDFI1, DDFI2, DDFI3 (+1 more)

### Community 24 - "SetupFragment.java"
Cohesion: 0.10
Nodes (16): android.content.Intent, android.preference.Preference, android.preference.Preference.OnPreferenceChangeListener, android.preference.PreferenceFragment, android.widget.Button, Intent, Override, PrefsActivity (+8 more)

### Community 25 - "EEPROMFragmentSafInstrumentedTest"
Cohesion: 0.17
Nodes (7): Activity, EEPROMFragmentSafInstrumentedTest, ActivityScenario, Context, Intent, Uri, EcmSimFixtures

### Community 26 - "JdbcEcmDefinitionsProvider"
Cohesion: 0.21
Nodes (6): JdbcEcmDefinitionsProvider, FakeEcmTransport, EEPROM, PDU, StateFlow, TestEcmTools

### Community 27 - "TestEcmProtocol"
Cohesion: 0.21
Nodes (8): FakeEcmServer, AutoCloseable, ByteArray, EEPROM, PDU, NoOpBitSetProvider, NoOpDefinitionsProvider, TestEcmProtocol

### Community 28 - "EEPROMFragment"
Cohesion: 0.14
Nodes (6): android.view.ContextMenu, android.widget.TextView, EEPROMFragment, Intent, Override, ContextMenuInfo

### Community 29 - "VariableProvider"
Cohesion: 0.11
Nodes (6): TestVariableProvider, Override, ByteArray, Context, VariableProvider, ArrayAdapter

### Community 30 - "TransportFactory"
Cohesion: 0.20
Nodes (7): BluetoothDevice, ByteArray, Context, UsbSerialConnection, UsbSerialPort, TransportFactory, UsbSerialConnection

### Community 31 - "EcmServiceBroadcastInstrumentedTest.kt"
Cohesion: 0.18
Nodes (10): EcmServiceBroadcastInstrumentedTest, BroadcastReceiver, FakeTcpEcmServer, AutoCloseable, Context, Intent, PDU, Socket (+2 more)

### Community 32 - "JdbcEcmDefinitions.kt"
Cohesion: 0.16
Nodes (10): DataSource, EEPROM, RUNTIME_DATA, BitSetRow, EepromPageRow, DatabaseBitSetProvider, Units, JdbcBitSetProvider (+2 more)

### Community 33 - "KoogEcmToolAdapter.kt"
Cohesion: 0.24
Nodes (14): ecmToolRegistry(), GetEcmInfoTool, GetEepromParameterArgs, GetEepromParameterTool, GetFuelMapRegionArgs, GetFuelMapRegionTool, ListLiveVariablesTool, NoArgs (+6 more)

### Community 34 - "ServiceTestSupport.kt"
Cohesion: 0.12
Nodes (8): Fail, FakeOutcome, EEPROM, newEcm(), NoOpBitSetProvider, NoOpDefinitionsProvider, NoOpVariableProvider, Reply

### Community 35 - "FakePduServer"
Cohesion: 0.18
Nodes (10): BleHarness, BleTransportContractTest, EcmTransportContractTest, CompletableDeferred, TransportHarness, ackWithBytes(), FakePduServer, AutoCloseable (+2 more)

### Community 36 - "EcmTools"
Cohesion: 0.15
Nodes (10): EcmTools, EepromNotRead, Error, NotConnected, Ok, ToolResult, Connection, Connection (+2 more)

### Community 37 - "ProgressDialogTask"
Cohesion: 0.10
Nodes (13): android.app.Activity, android.app.ProgressDialog, android.content.DialogInterface.OnCancelListener, android.os.AsyncTask, android.os.Handler, AboutActivity, Override, Override (+5 more)

### Community 38 - "LogFragment"
Cohesion: 0.16
Nodes (6): Interval, Override, LogFragment, StopTask, biz.logicminds.buelltune.util.Bin2MslConverter, java.util.Observer

### Community 39 - "TcpTransport"
Cohesion: 0.17
Nodes (8): PDU, Socket, StateFlow, TcpTransport, CompletableDeferred, TcpHarness, TcpTransportContractTest, TcpTransportTest

### Community 40 - "android.view.View"
Cohesion: 0.11
Nodes (14): android.app.Dialog, android.app.DialogFragment, android.content.DialogInterface.OnClickListener, android.view.KeyEvent, android.view.View, android.view.View.OnKeyListener, android.widget.EditText, ActiveTestsFragment (+6 more)

### Community 41 - "JdbcVariableProvider"
Cohesion: 0.19
Nodes (4): BitNamesRow, EeVariableRow, RtVariableRow, JdbcVariableProvider

### Community 42 - "ConnectionState"
Cohesion: 0.17
Nodes (13): EcmLiveDataSource, AutoCloseable, StateFlow, ServiceEcmLiveDataSource, Connected, Connecting, ConnectionState, Disconnected (+5 more)

### Community 43 - ".connectedEcm"
Cohesion: 0.30
Nodes (4): ByteArray, PDU, PollRecordLoopTest, RecordingSinkSpy

### Community 44 - "BuellTune Read-Only ECM Chat Plan"
Cohesion: 0.20
Nodes (17): ChatAgent Agentic Loop, Chat Architecture and Tool Layer (Section 16), ChatFragment Dual-FragmentManager Hosting, Conversation Persistence and Resume-Replay Rule, EcmTools, KoogEcmToolAdapter, Suggestion Cards, ChatAgent Agentic Loop (U5) (+9 more)

### Community 45 - "AppContainer"
Cohesion: 0.22
Nodes (8): android.app.Application, AppContainer, Context, ChatDatabase, Context, RoomDatabase, EcmDroidApp, Override

### Community 46 - "Visual Rebrand - App Icon and Docs Naming Pass Plan"
Cohesion: 0.15
Nodes (16): About Screen (about.html), EcmDroid Fork Attribution, Release v0.1.1, Android Asset Derivation Process, Brand Palette, KD1: New Artwork Original, Not Derived, KD2: Full Adaptive Icon, Visual Rebrand - App Icon and Docs Naming Pass Plan (+8 more)

### Community 47 - "EcmSimConnectionLossIntegrationTest.kt"
Cohesion: 0.21
Nodes (7): Recording, RecordingState, Stopped, CapturingRecordingSink, EcmSimConnectionLossIntegrationTest, ByteArray, Connection

### Community 48 - "BuellTune Kotlin Foundation and Compliance Plan"
Cohesion: 0.18
Nodes (16): AppContainer DI Composition Root Pattern (KTD5), KD1: Rebrand as Hard Fork, KD8: Big-Bang Package Rename, BuellTune Kotlin Foundation and Compliance Plan, U1: Big-Bang Rename to biz.logicminds.buelltune, U2: Play Store Compliance, U3: Enable Kotlin, KSP, Compose, U4: Port Pure-Java Domain Classes to Kotlin (+8 more)

### Community 49 - "ChatMessageAdapter"
Cohesion: 0.24
Nodes (8): Adapter, ChatMessageAdapter, ConversationAdapter, Button, ViewGroup, ViewHolder, LinearLayout, Markwon

### Community 50 - "EcmDefinitionsDatabase"
Cohesion: 0.31
Nodes (4): TestEcmDefinitionsDatabase, EcmDefinitionsDatabase, Context, RoomDatabase

### Community 51 - "ChatMessageEntity"
Cohesion: 0.17
Nodes (5): ChatMessageDao, ConversationDao, ConversationWithPreview, Flow, ChatMessageEntity

### Community 52 - ".recordingBytesMatchGoldenDataOutputStreamEncoding"
Cohesion: 0.16
Nodes (10): CapturingSink, FakeClock, ByteArray, RecordingFormatTest, ackWithBytes(), FakeEcmTransport, FixedIdDefinitionsProvider, ByteArray (+2 more)

### Community 53 - "ecmsim ECM Simulator"
Cohesion: 0.18
Nodes (13): ecmsim-backed JVM Integration Suite, buelltune-banner.svg, Digital Wing Brand Identity, icon.svg, Testing (Section 13), ecmsim-backed JVM Integration Suite (R16/R17), U2: BuellTune Product-Naming Docs Pass, User Guide (+5 more)

### Community 54 - "TestChatAgent.kt"
Cohesion: 0.26
Nodes (9): Connection, EEPROM, Flow, JsonObject, LLModel, ModerationResult, Prompt, StreamFrame (+1 more)

### Community 56 - ".gatedTransport"
Cohesion: 0.33
Nodes (5): BluetoothClassicTransportContractTest, BtHarness, BluetoothSocket, CompletableDeferred, mockConnectedSocket()

### Community 57 - "fakeUsbConnection"
Cohesion: 0.21
Nodes (8): fakeUsbConnection(), UsbSerialConnection, ByteArray, CompletableDeferred, SerialInputOutputManager, UsbSerialConnection, UsbHarness, UsbSerialTransportContractTest

### Community 58 - "EcmDefinitionsProvider"
Cohesion: 0.22
Nodes (4): FakeDefinitionsProvider, EcmDefinitionsProvider, EEPROM, RoomEcmDefinitionsProvider

### Community 59 - "Developer Guide"
Cohesion: 0.21
Nodes (12): Async Task Pattern (Section 11), Background Service and Threading (Section 9), Binary Log Format and MSL Conversion (Section 12), Developer Guide, Bundled ECM-Definitions Database (Section 8), ECM Central Facade (Section 6), EEPROM/Variable/BitSet Data Model (Section 7), PDU Wire Protocol (Section 4) (+4 more)

### Community 60 - "prepare_release.py"
Cohesion: 0.29
Nodes (12): build_changelog_section(), bump_semver(), commit_range_subjects(), find_since_ref(), insert_changelog_section(), main(), [(short_sha, subject), ...] oldest first, excluding merges and prior release…, Previous release tag, else the commit that added CHANGELOG.md, else None. (+4 more)

### Community 62 - "BluetoothClassicTransport"
Cohesion: 0.27
Nodes (5): BluetoothClassicTransport, BluetoothSocket, PDU, StateFlow, BluetoothClassicTransportTest

### Community 63 - "Bin2MslConverter"
Cohesion: 0.25
Nodes (4): Bin2MslConverter, Observable, DecimalFormat, java.util.Observable

### Community 64 - "BuellTune Banner (SVG)"
Cohesion: 0.21
Nodes (12): BuellTune Banner (SVG), Background Gradient (bg-grad), Telemetry Cyan Glow Filter (cyan-glow), Telemetry Cyan Gradient (cyan-grad), Digital Wing Logo Mark, Wing Feathers as Power/RPM Equalizer Bars, Flame Gradient — Buell Racing Orange (flame-grad), Metallic Wing Gradient (metal-grad) (+4 more)

### Community 65 - "MainActivityBluetoothPermissionInstrumentedTest"
Cohesion: 0.36
Nodes (3): ActivityScenario, Context, MainActivityBluetoothPermissionInstrumentedTest

### Community 66 - "BitSetProvider"
Cohesion: 0.19
Nodes (4): TestBitSetProvider, BitSetProvider, TestUtils, org.junit.Before

### Community 67 - "EcmService.kt"
Cohesion: 0.22
Nodes (7): EcmServiceBinder, Intent, Binder, IBinder, Notification, NotificationManager, Service

### Community 68 - "android.os.Bundle"
Cohesion: 0.12
Nodes (9): android.os.Bundle, android.preference.PreferenceActivity, android.view.View.OnClickListener, Override, Override, SuppressWarnings, TorqueValuesFragment, Override (+1 more)

### Community 69 - "ChatAgent.kt"
Cohesion: 0.22
Nodes (7): ChatAgent, Role, ASSISTANT, USER, extractSuggestion(), SuggestionCard, PromptExecutor

### Community 70 - "DataType"
Cohesion: 0.20
Nodes (10): DataType, ARRAY, AXIS, BITFIELD, BITS, MAP, SCALAR, STRING (+2 more)

### Community 71 - "mvnw"
Cohesion: 0.38
Nodes (8): mvnw script, clean(), die(), exec_maven(), hash_string(), set_java_home(), trim(), verbose()

### Community 72 - "AGENTS.md Repository Guidelines"
Cohesion: 0.22
Nodes (8): AGENTS.md Repository Guidelines, graphify Knowledge Graph, CI Workflow, Instrumented Tests CI Job, create-release Workflow, scripts/prepare_release.py, Release Workflow, tag-release Workflow

### Community 73 - "Beads (bd) Issue Tracker"
Cohesion: 0.25
Nodes (9): AGENTS.md Beads Codex Setup Block, AGENTS.md Beads Integration Block, Beads OpenAI Agent Interface, bd CLI, Beads Skill Documentation, Beads Repository Configuration, Beads (bd) Issue Tracker, Dolt (+1 more)

### Community 74 - "FileOutputStream"
Cohesion: 0.39
Nodes (3): Context, LegacyFragmentBridgeInstrumentedTest, FileOutputStream

### Community 77 - "EcmTransport"
Cohesion: 0.29
Nodes (3): EcmTransport, PDU, StateFlow

### Community 79 - "Constants"
Cohesion: 0.29
Nodes (4): FakeBitSetProvider, Constants, Variables, Pattern

### Community 82 - "BuellTune App Icon (Digital Wing / Pegasus Mark)"
Cohesion: 0.48
Nodes (7): BuellTune App Icon (Digital Wing / Pegasus Mark), cyan-glow: Gaussian blur glow filter for telemetry accents, cyan-grad: Telemetry Cyan gradient (#00E5FF to #0088FF), Digital Node Circuit Overlay: dashed/solid connector lines and glowing nodes tracing the wing silhouette, flame-grad: Buell Racing Orange gradient (#FF5500 to #FF2A00), logo-mark group: metallic Pegasus crest with flame-gradient wing feathers and cyan digital circuit overlay, metal-grad: Metallic Wing gradient (#F4F5F7 to #4A505A)

### Community 84 - "ECM Facade"
Cohesion: 0.40
Nodes (5): BuellTune App, ECM Facade, EcmService, EEPROM Data Model, PDU Wire Protocol

### Community 86 - "ScanState"
Cohesion: 0.40
Nodes (5): ScanState, DISCOVERY, DISCOVERY_FINISHED, LE_SCAN, NONE

### Community 87 - "BuellTune App Branding Identity"
Cohesion: 0.60
Nodes (5): BuellTune App Branding Identity, Ascending Bar Chart Motif, BuellTune Logo, Neon Cyan Trend Line, Piston/Wedge Glyph

### Community 88 - "Splash Banner (Full-Screen Splash Background Asset)"
Cohesion: 0.50
Nodes (5): Splash Banner (Full-Screen Splash Background Asset), BuellTune Ascending Bar-Chart Logo Mark (gray-to-orange bars with cyan dotted trend line), ECU DIAGNOSTICS & CALIBRATION Tagline, BUELL TUNE Wordmark (white BUELL + blue-glow TUNE), activity_splash.xml (SplashActivity FrameLayout)

### Community 91 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 92 - "BuellTune Brand Banner (docs/branding/buelltune-banner.png)"
Cohesion: 1.00
Nodes (3): BuellTune Brand Banner (docs/branding/buelltune-banner.png), Tagline: "ECU Diagnostics & Calibration", BuellTune Visual Identity (bar-chart/bolt mark, orange-to-blue gradient, dark theme)

## Ambiguous Edges - Review These
- `User Guide` → `Privacy Policy`  [AMBIGUOUS]
  privacy-policy.md · relation: conceptually_related_to

## Knowledge Gaps
- **114 isolated node(s):** `BuellTuneDestinations`, `Variables`, `RUNTIME_DATA`, `EEPROM`, `DDFI1` (+109 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 371 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **32 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `User Guide` and `Privacy Policy`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `ECM` connect `ECM` to `AppPreferences`, `EcmService`, `EEPROM`, `PDU`, `EcmSimProtocolIntegrationTest`, `MainActivity.java`, `Variable`, `PollRecordLoop`, `TestChatAgent`, `org.junit.Test`, `ProviderId`, `ChatRepository`, `Bit`, `EEPROMFragmentSafInstrumentedTest`, `JdbcEcmDefinitionsProvider`, `TestEcmProtocol`, `EEPROMFragment`, `VariableProvider`, `EcmServiceBroadcastInstrumentedTest.kt`, `JdbcEcmDefinitions.kt`, `ServiceTestSupport.kt`, `EcmTools`, `ConnectionState`, `.connectedEcm`, `AppContainer`, `EcmSimConnectionLossIntegrationTest.kt`, `.recordingBytesMatchGoldenDataOutputStreamEncoding`, `TestChatAgent.kt`, `EcmToolsIntegrationTest`, `EcmService.kt`, `android.os.Bundle`, `FileOutputStream`, `EcmTransport`, `.startRecording`?**
  _High betweenness centrality (0.256) - this node is a cross-community bridge._
- **Why does `PDU` connect `PDU` to `ServiceTestSupport.kt`, `FakePduServer`, `TcpTransport`, `EcmSimProtocolIntegrationTest`, `.connectedEcm`, `EcmTransport`, `UsbSerialTransport`, `org.junit.Test`, `.recordingBytesMatchGoldenDataOutputStreamEncoding`, `BleTransport`, `TestChatAgent.kt`, `fakeUsbConnection`, `BluetoothClassicTransport`, `EcmServiceBroadcastInstrumentedTest.kt`?**
  _High betweenness centrality (0.096) - this node is a cross-community bridge._
- **Why does `AppContainer` connect `AppContainer` to `JdbcEcmDefinitions.kt`, `BitSetProvider`, `ECM`, `EcmTools`, `EcmService.kt`, `ConnectionStatusViewModel`, `ChatFragment`, `UsbSerialTransport`, `EcmDefinitionsDatabase`, `VariableProvider`, `ProviderId`, `ChatRepository`, `EcmDefinitionsProvider`, `DatabaseVariableProvider`?**
  _High betweenness centrality (0.095) - this node is a cross-community bridge._
- **Are the 3 inferred relationships involving `PDU` (e.g. with `.testCorruptedChecksumRejected()` and `.testGetResponse()`) actually correct?**
  _`PDU` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `BuellTuneDestinations`, `Variables`, `RUNTIME_DATA` to the rest of the system?**
  _114 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `AppPreferences` be split into smaller, more focused modules?**
  _Cohesion score 0.1329268292682927 - nodes in this community are weakly interconnected._