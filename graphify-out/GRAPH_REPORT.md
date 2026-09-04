# Graph Report - kownledge-graph  (2026-09-03)

## Corpus Check
- 144 files · ~123,720 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1471 nodes · 3332 edges · 90 communities (60 shown, 24 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 296 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- PDU Protocol Encoding
- BLE Transport
- EEPROM Definitions Provider
- Android BLE GATT & Bluetooth APIs
- Beads Issue Tracker
- Android Content Provider & Cursor APIs
- Android Framework Bluetooth/UI/Service APIs
- EEPROM Paging
- Serial Byte Stream I/O
- Debug Connection Screen Tests
- Preferences Screen
- Variable Value Formatting
- About Screen
- EcmService Broadcast Tests
- ECM Variable Constants & Fake Server
- PollRecordLoop Polling/Recording
- EEPROM Bit Handling
- DataChannel List Adapter
- Manifest Compliance & ECM Unit Tests
- Variable Provider Tests
- EcmService Lifecycle
- ECM Facade Core
- EEPROM SAF Tests
- App Preferences
- Bluetooth Device List Fragment
- Log Recording Fragment
- EcmTransport Abstraction
- AsyncTask Progress Dialog
- Bits DAO (Room)
- EEPROM/Units DB Rows
- EcmTransport Contract Tests
- EEPROM Fragment UI
- Active Tests Fragment
- TCP Transport
- Cell Editor Dialog
- AppContainer DI Container
- EcmLiveDataSource
- DataChannel Fragment Logic
- Names/Pages DAO (Room)
- Recording State Machine
- JDBC Variable Provider (JVM tests)
- EcmDefinitionsDatabase Tests
- Brand Banner Visual Concepts
- Active Test Function Constants
- USB Serial Transport Tests
- ECM Simulator Process Control
- Release Changelog Script
- ECM Legacy Facade & Error Types
- Bluetooth Permission Instrumented Tests
- Database Variable Provider
- EcmService Binder
- Bluetooth Classic Transport
- EEPROM Variable Data Types
- Test Fixture Utilities
- Bluetooth Classic Transport Tests
- Bin2Msl Log Converter
- Hex Formatting Test Utilities
- Splash Screen
- BitSet Provider
- RT Offsets DAO
- ECM Protocol Enum
- Legacy EEPROM Async Task
- DataOutputStream Recording Sink
- BLE Scan State
- BLE Transport Test Harness
- Brand Identity Concepts
- Gradle Wrapper Script
- Logo Performance Motif
- Adaptive Icon Guides
- Telemetry Cyan Accent Concepts
- ECM Simulator Integration Suite
- Git post-checkout Hook
- Git post-merge Hook
- Git pre-commit Hook
- Git pre-push Hook
- Git prepare-commit-msg Hook
- Racing Orange Flame Gradient
- Metallic Wing Gradient
- MySQL-to-SQLite Script
- Launcher Icon (hdpi)
- Launcher Icon (mdpi)
- Launcher Icon Concept
- Launcher Icon (xxxhdpi)
- Digital Wing Logo Mark

## God Nodes (most connected - your core abstractions)
1. `PDU` - 72 edges
2. `ECM` - 71 edges
3. `Variable` - 51 edges
4. `MainActivity` - 41 edges
5. `SerialSocket` - 34 edges
6. `PollRecordLoop` - 33 edges
7. `EcmService` - 30 edges
8. `EcmTransport` - 29 edges
9. `ConnectionState` - 27 edges
10. `DevicesFragment` - 26 edges

## Surprising Connections (you probably didn't know these)
- `AGENTS.md Repository Guidelines` --shares_data_with--> `CLAUDE.md Project Instructions`  [INFERRED]
  AGENTS.md → CLAUDE.md
- `ecmdroid Privacy Policy` --conceptually_related_to--> `BuellTune User Guide`  [AMBIGUOUS]
  privacy-policy.md → docs/USER_GUIDE.md
- `ecmsim-Backed JVM Integration Test Harness` --shares_data_with--> `ecmsim Integration Test CI Job`  [INFERRED]
  docs/plans/2026-08-30-001-refactor-kotlin-foundation-compliance-plan.md → .github/workflows/ci.yml
- `Kotlin Foundation Rebrand Initiative` --rationale_for--> `AGENTS.md Repository Guidelines`  [INFERRED]
  docs/plans/2026-08-30-001-refactor-kotlin-foundation-compliance-plan.md → AGENTS.md
- `BuellTune CHANGELOG` --references--> `BuellTune Kotlin Foundation & Compliance Plan`  [INFERRED]
  CHANGELOG.md → docs/plans/2026-08-30-001-refactor-kotlin-foundation-compliance-plan.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Beads (bd) Issue-Tracking Integration Across Skill, Config, and Agent Instructions** — _agents_skills_beads_skill_doc, _agents_skills_beads_agents_openai_doc, _beads_readme_doc, _beads_config_doc, agents_doc, claude_doc [EXTRACTED 1.00]
- **BuellTune Release Automation Pipeline (bump/PR -> tag -> signed publish)** — _github_workflows_create_release_doc, _github_workflows_tag_release_doc, _github_workflows_release_doc [EXTRACTED 1.00]
- **BuellTune Visual Rebrand Plan Lineage (Kotlin foundation -> icon/docs -> theme/screens)** — docs_plans_2026_08_30_001_refactor_kotlin_foundation_compliance_plan_doc, docs_plans_2026_09_01_001_feat_visual_rebrand_icon_docs_plan_doc, docs_plans_2026_09_01_002_feat_visual_rebrand_theme_screens_plan_doc [EXTRACTED 1.00]
- **Buell Tune Splash Branding Composition (logo + wordmark + tagline)** — app_src_main_res_drawable_nodpi_splash_banner_splashscreen, app_src_main_res_drawable_nodpi_splash_banner_buelltunelogo, app_src_main_res_drawable_nodpi_splash_banner_buelltunebrand, app_src_main_res_drawable_nodpi_splash_banner_ecudiagnosticscalibration [INFERRED 0.85]
- **BuellTune Brand Identity Composition** — docs_branding_buelltune_banner_buelltune_brand, docs_branding_buelltune_banner_tagline, docs_branding_buelltune_banner_logo_icon, docs_branding_buelltune_banner_color_scheme [EXTRACTED 1.00]
- **BuellTune Visual Identity Color/Gradient System** — docs_branding_buelltune_banner_flame_gradient, docs_branding_buelltune_banner_cyan_gradient, docs_branding_buelltune_banner_metal_gradient, docs_branding_buelltune_banner_grid_pattern [EXTRACTED 1.00]
- **Hardware-Software Fusion Motif (Pegasus Wing + Circuit Telemetry + Wordmark)** — docs_branding_buelltune_banner_logo_mark, docs_branding_buelltune_banner_circuit_overlay, docs_branding_buelltune_banner_wordmark, docs_branding_buelltune_banner_design_concept [EXTRACTED 1.00]
- **Digital Wing Logo Mark Composition (crest + feathers + circuit overlay grouped under logo-mark)** — docs_branding_icon_digital_wing_mark, docs_branding_icon_pegasus_crest, docs_branding_icon_wing_feathers, docs_branding_icon_digital_node_circuit_overlay [EXTRACTED 1.00]
- **Telemetry Accent System (cyan nodes, connector lines and glow filter grouped together)** — docs_branding_icon_digital_node_circuit_overlay, docs_branding_icon_cyan_gradient, docs_branding_icon_cyan_glow_filter [EXTRACTED 1.00]

## Communities (90 total, 24 thin omitted)

### Community 0 - "PDU Protocol Encoding"
Cohesion: 0.06
Nodes (17): ByteArray, PDU, ByteArray, PduFraming, ByteArray, EcmSimProtocolIntegrationTest, Connection, JdbcEcmDefinitionsProvider (+9 more)

### Community 1 - "BLE Transport"
Cohesion: 0.06
Nodes (29): BleOutputStream, BleSerialSocket, BleTransport, SerialListener, Connect, ConnectError, IoError, BleSerialSocket (+21 more)

### Community 2 - "EEPROM Definitions Provider"
Cohesion: 0.06
Nodes (23): EcmDefinitionsProvider, EEPROM, RoomEcmDefinitionsProvider, ByteArray, PollRecordLoopTest, RecordingSinkSpy, CapturingSink, FakeClock (+15 more)

### Community 3 - "Android BLE GATT & Bluetooth APIs"
Cohesion: 0.09
Nodes (16): android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCallback, android.bluetooth.BluetoothGattCharacteristic, android.bluetooth.BluetoothGattDescriptor, android.bluetooth.BluetoothGattService, android.content.Intent, android.content.IntentFilter, SerialListener (+8 more)

### Community 4 - "Beads Issue Tracker"
Cohesion: 0.08
Nodes (44): Beads OpenAI Agent Interface Config, bd CLI Workflow, Beads Issue Tracker (bd), Beads Skill Guide, Beads Repository Configuration, Dolt Remote Sync (DoltHub), Beads AI-Native Issue Tracking README, CI GitHub Actions Workflow (+36 more)

### Community 5 - "Android Content Provider & Cursor APIs"
Cohesion: 0.08
Nodes (15): android.content.ContentProvider, android.content.ContentValues, android.content.Context, android.database.Cursor, android.net.Uri, android.os.ParcelFileDescriptor, android.widget.BaseAdapter, android.widget.SectionIndexer (+7 more)

### Community 6 - "Android Framework Bluetooth/UI/Service APIs"
Cohesion: 0.12
Nodes (18): android.bluetooth.BluetoothDevice, android.content.BroadcastReceiver, android.content.res.ColorStateList, android.content.ServiceConnection, android.os.IBinder, android.view.MenuItem, android.widget.ToggleButton, androidx.appcompat.app.AppCompatActivity (+10 more)

### Community 7 - "EEPROM Paging"
Cohesion: 0.10
Nodes (5): EEPROM, ByteArray, Context, Page, Override

### Community 8 - "Serial Byte Stream I/O"
Cohesion: 0.10
Nodes (16): ByteArray, InputStream, PolledByteQueueInputStream, Data, Error, ByteArray, CompletableDeferred, Flow (+8 more)

### Community 9 - "Debug Connection Screen Tests"
Cohesion: 0.10
Nodes (20): ConnectionStatusScreenTest, FakeEcmLiveDataSource, StateFlow, BuellTuneDebugActivity, Bundle, BuellTuneDestinations, BuellTuneNavHost(), BuellTuneTheme() (+12 more)

### Community 10 - "Preferences Screen"
Cohesion: 0.11
Nodes (17): android.preference.Preference, android.preference.Preference.OnPreferenceChangeListener, android.preference.PreferenceActivity, android.preference.PreferenceFragment, android.preference.PreferenceGroup, android.widget.Button, Intent, Override (+9 more)

### Community 11 - "Variable Value Formatting"
Cohesion: 0.09
Nodes (4): Variable, NoOpVariableProvider, StubVariableProvider, DecimalFormat

### Community 12 - "About Screen"
Cohesion: 0.14
Nodes (13): android.app.Activity, android.app.Fragment, android.content.DialogInterface, android.widget.ArrayAdapter, android.widget.Spinner, android.widget.TextView, AboutActivity, Override (+5 more)

### Community 13 - "EcmService Broadcast Tests"
Cohesion: 0.13
Nodes (12): EcmServiceBroadcastInstrumentedTest, BroadcastReceiver, FakeTcpEcmServer, AutoCloseable, Context, Intent, Socket, Context (+4 more)

### Community 14 - "ECM Variable Constants & Fake Server"
Cohesion: 0.15
Nodes (10): Constants, Variables, FakeEcmServer, AutoCloseable, ByteArray, EEPROM, NoOpBitSetProvider, NoOpDefinitionsProvider (+2 more)

### Community 15 - "PollRecordLoop Polling/Recording"
Cohesion: 0.15
Nodes (9): bigEndianInt(), Clock, ByteArray, Flow, Job, StateFlow, PollRecordLoop, RecordingSink (+1 more)

### Community 16 - "EEPROM Bit Handling"
Cohesion: 0.11
Nodes (9): Bit, ByteArray, BitSet, ByteArray, Type, DDFI1, DDFI2, DDFI3 (+1 more)

### Community 17 - "DataChannel List Adapter"
Cohesion: 0.19
Nodes (11): android.os.Bundle, android.view.LayoutInflater, android.view.View, android.view.ViewGroup, androidx.annotation.NonNull, androidx.annotation.Nullable, DataChannelAdapter, Override (+3 more)

### Community 18 - "Manifest Compliance & ECM Unit Tests"
Cohesion: 0.17
Nodes (12): androidx.test.ext.junit.runners.AndroidJUnit4, ManifestComplianceInstrumentedTest, TestECM, TestEEPROM, TestBin2Msl, TestByteSemantics, biz.logicminds.buelltune.AppContainer, biz.logicminds.buelltune.Error.ErrorType (+4 more)

### Community 19 - "Variable Provider Tests"
Cohesion: 0.15
Nodes (3): TestVariableProvider, ByteArray, VariableProvider

### Community 20 - "EcmService Lifecycle"
Cohesion: 0.13
Nodes (3): EcmService, LegacyBroadcastBridge, CoroutineScope

### Community 22 - "EEPROM SAF Tests"
Cohesion: 0.17
Nodes (7): Activity, EEPROMFragmentSafInstrumentedTest, ActivityScenario, Context, Intent, Uri, EcmSimFixtures

### Community 23 - "App Preferences"
Cohesion: 0.18
Nodes (4): AppPreferences, Activity, Context, SharedPreferences

### Community 24 - "Bluetooth Device List Fragment"
Cohesion: 0.15
Nodes (9): android.bluetooth.BluetoothAdapter, android.view.Menu, android.view.MenuInflater, DevicesFragment, ArrayAdapter, Handler, IntentFilter, Override (+1 more)

### Community 25 - "Log Recording Fragment"
Cohesion: 0.13
Nodes (8): androidx.documentfile.provider.DocumentFile, Interval, Override, LogFragment, StopTask, biz.logicminds.buelltune.util.Bin2MslConverter, java.util.Observable, java.util.Observer

### Community 26 - "EcmTransport Abstraction"
Cohesion: 0.15
Nodes (9): EcmTransport, StateFlow, BluetoothDevice, ByteArray, Context, UsbSerialConnection, UsbSerialPort, TransportFactory (+1 more)

### Community 27 - "AsyncTask Progress Dialog"
Cohesion: 0.15
Nodes (9): android.app.ProgressDialog, android.content.DialogInterface.OnCancelListener, android.os.AsyncTask, Override, ProgressDialogTask, Activity, Context, Menu (+1 more)

### Community 28 - "Bits DAO (Room)"
Cohesion: 0.12
Nodes (7): AdxbitsDao, BitNamesRow, BitsDao, EeoffsetsDao, EeVariableRow, RtVariableRow, BitsEntity

### Community 29 - "EEPROM/Units DB Rows"
Cohesion: 0.16
Nodes (8): BitSetRow, EepromPageRow, Units, AssetDatabase, JdbcBitSetProvider, Connection, EEPROM, nullableInt()

### Community 30 - "EcmTransport Contract Tests"
Cohesion: 0.22
Nodes (6): EcmTransportContractTest, CompletableDeferred, ackWithBytes(), FakePduServer, AutoCloseable, ByteArray

### Community 31 - "EEPROM Fragment UI"
Cohesion: 0.19
Nodes (6): android.view.ContextMenu, EEPROM, EEPROMFragment, Intent, Override, ContextMenuInfo

### Community 32 - "Active Tests Fragment"
Cohesion: 0.19
Nodes (7): android.app.ListFragment, android.view.View.OnClickListener, android.widget.ListView, ActiveTestsFragment, FunctionTask, Override, OnClickListener

### Community 33 - "TCP Transport"
Cohesion: 0.18
Nodes (7): Socket, StateFlow, TcpTransport, CompletableDeferred, TcpHarness, TcpTransportContractTest, TcpTransportTest

### Community 34 - "Cell Editor Dialog"
Cohesion: 0.21
Nodes (9): android.annotation.SuppressLint, android.app.Dialog, android.app.DialogFragment, android.content.DialogInterface.OnClickListener, android.view.KeyEvent, android.view.View.OnKeyListener, CellEditorDialogFragment, CellEditorDialogListener (+1 more)

### Community 35 - "AppContainer DI Container"
Cohesion: 0.19
Nodes (7): android.app.Application, TestBitSetProvider, AppContainer, Context, BitSetProvider, EcmDroidApp, Override

### Community 36 - "EcmLiveDataSource"
Cohesion: 0.17
Nodes (13): EcmLiveDataSource, AutoCloseable, StateFlow, ServiceEcmLiveDataSource, Connected, Connecting, ConnectionState, Disconnected (+5 more)

### Community 38 - "Names/Pages DAO (Room)"
Cohesion: 0.12
Nodes (6): NamesDao, PagesDao, AdxbitsEntity, EeoffsetsEntity, NamesEntity, PagesEntity

### Community 39 - "Recording State Machine"
Cohesion: 0.21
Nodes (7): Recording, RecordingState, Stopped, CapturingRecordingSink, EcmSimConnectionLossIntegrationTest, ByteArray, Connection

### Community 41 - "EcmDefinitionsDatabase Tests"
Cohesion: 0.16
Nodes (6): TestEcmDefinitionsDatabase, EepromDao, EcmDefinitionsDatabase, Context, EepromEntity, RoomDatabase

### Community 42 - "Brand Banner Visual Concepts"
Cohesion: 0.17
Nodes (15): BuellTune Banner Logo (SVG), BuellTune (Brand Name), Digital Node Circuit Overlay (Software/ECU Tuning Link), Cyan-to-Orange Gradient Color Scheme, Telemetry Cyan Gradient (#00E5FF -> #0088FF) with Glow Filter, Hardware/Software Fusion Brand Concept, Wing Feather Bars / Power-Band Equalizer (Low-Range, Mid-Range, Peak RPM, Apex Wingtip), Buell Racing Orange Flame Gradient (#FF5500 -> #FF2A00) (+7 more)

### Community 43 - "Active Test Function Constants"
Cohesion: 0.14
Nodes (13): Function, Active_Intake, ClearCodes, Exh_Valve, Fan, FrontCoil, FrontInj, FuelPump (+5 more)

### Community 44 - "USB Serial Transport Tests"
Cohesion: 0.21
Nodes (8): fakeUsbConnection(), UsbSerialConnection, ByteArray, CompletableDeferred, SerialInputOutputManager, UsbSerialConnection, UsbHarness, UsbSerialTransportContractTest

### Community 46 - "ECM Simulator Process Control"
Cohesion: 0.22
Nodes (3): EcmSimProcess, EcmSimRule, ExternalResource

### Community 47 - "Release Changelog Script"
Cohesion: 0.29
Nodes (12): build_changelog_section(), bump_semver(), commit_range_subjects(), find_since_ref(), insert_changelog_section(), main(), [(short_sha, subject), ...] oldest first, excluding merges and prior release…, Previous release tag, else the commit that added CHANGELOG.md, else None. (+4 more)

### Community 48 - "ECM Legacy Facade & Error Types"
Cohesion: 0.17
Nodes (7): Context, Error, ErrorType, CURRENT, RECENT, STORED, Override

### Community 49 - "Bluetooth Permission Instrumented Tests"
Cohesion: 0.36
Nodes (3): ActivityScenario, Context, MainActivityBluetoothPermissionInstrumentedTest

### Community 51 - "EcmService Binder"
Cohesion: 0.22
Nodes (7): EcmServiceBinder, Intent, Binder, IBinder, Notification, NotificationManager, Service

### Community 52 - "Bluetooth Classic Transport"
Cohesion: 0.31
Nodes (4): BluetoothClassicTransport, BluetoothSocket, StateFlow, BluetoothClassicTransportTest

### Community 53 - "EEPROM Variable Data Types"
Cohesion: 0.20
Nodes (10): DataType, ARRAY, AXIS, BITFIELD, BITS, MAP, SCALAR, STRING (+2 more)

### Community 55 - "Bluetooth Classic Transport Tests"
Cohesion: 0.33
Nodes (5): BluetoothClassicTransportContractTest, BtHarness, BluetoothSocket, CompletableDeferred, mockConnectedSocket()

### Community 58 - "Splash Screen"
Cohesion: 0.38
Nodes (3): android.os.Handler, Override, SplashActivity

### Community 59 - "BitSet Provider"
Cohesion: 0.33
Nodes (4): DataSource, EEPROM, RUNTIME_DATA, DatabaseBitSetProvider

### Community 62 - "ECM Protocol Enum"
Cohesion: 0.40
Nodes (3): Protocol, FACTORY_RACE, STOCK

### Community 65 - "BLE Scan State"
Cohesion: 0.40
Nodes (5): ScanState, DISCOVERY, DISCOVERY_FINISHED, LE_SCAN, NONE

### Community 66 - "BLE Transport Test Harness"
Cohesion: 0.50
Nodes (3): BleHarness, BleTransportContractTest, TransportHarness

### Community 67 - "Brand Identity Concepts"
Cohesion: 0.83
Nodes (4): Buell Tune (Brand/Product Name), Buell Tune Ascending Bar-Chart Logo Icon, ECU Diagnostics & Calibration (App Purpose Tagline), Buell Tune Splash Screen Banner

### Community 68 - "Gradle Wrapper Script"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 69 - "Logo Performance Motif"
Cohesion: 1.00
Nodes (3): Ascending Performance Trend Chart Motif, BuellTune App Logo, Stock-to-Tuned Transformation Motif

### Community 70 - "Adaptive Icon Guides"
Cohesion: 0.67
Nodes (3): Adaptive Icon Keyline/Safe-Zone Guide Overlay, Ascending Performance-Bar / Power-Curve Motif, BuellTune Launcher Icon (mipmap-xhdpi)

### Community 71 - "Telemetry Cyan Accent Concepts"
Cohesion: 0.67
Nodes (3): Cyan Glow Filter for Telemetry Accents, Telemetry Cyan Gradient (#00E5FF → #0088FF), Digital Node Circuit Overlay (telemetry nodes and connector lines)

## Ambiguous Edges - Review These
- `BuellTune User Guide` → `ecmdroid Privacy Policy`  [AMBIGUOUS]
  privacy-policy.md · relation: conceptually_related_to

## Knowledge Gaps
- **74 isolated node(s):** `BuellTuneDestinations`, `Variables`, `RUNTIME_DATA`, `EEPROM`, `DDFI1` (+69 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 256 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **24 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `BuellTune User Guide` and `ecmdroid Privacy Policy`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `ECM` connect `ECM Facade Core` to `PDU Protocol Encoding`, `EEPROM Definitions Provider`, `EEPROM Paging`, `Variable Value Formatting`, `EcmService Broadcast Tests`, `ECM Variable Constants & Fake Server`, `PollRecordLoop Polling/Recording`, `EEPROM Bit Handling`, `DataChannel List Adapter`, `Manifest Compliance & ECM Unit Tests`, `EcmService Lifecycle`, `EEPROM SAF Tests`, `App Preferences`, `EcmTransport Abstraction`, `EEPROM/Units DB Rows`, `EEPROM Fragment UI`, `AppContainer DI Container`, `EcmLiveDataSource`, `DataChannel Fragment Logic`, `Recording State Machine`, `MainActivity Connection UI`, `ECM Legacy Facade & Error Types`, `EcmService Binder`, `ECM Protocol Enum`, `DataOutputStream Recording Sink`?**
  _High betweenness centrality (0.227) - this node is a cross-community bridge._
- **Why does `PDU` connect `PDU Protocol Encoding` to `BLE Transport`, `TCP Transport`, `EEPROM Definitions Provider`, `Serial Byte Stream I/O`, `Active Test Function Constants`, `USB Serial Transport Tests`, `EcmService Broadcast Tests`, `ECM Variable Constants & Fake Server`, `Bluetooth Classic Transport`, `ECM Facade Core`, `EcmTransport Abstraction`, `EcmTransport Contract Tests`?**
  _High betweenness centrality (0.126) - this node is a cross-community bridge._
- **Why does `EcmTransport` connect `EcmTransport Abstraction` to `BLE Transport`, `TCP Transport`, `EEPROM Definitions Provider`, `EcmLiveDataSource`, `BLE Transport Test Harness`, `Serial Byte Stream I/O`, `USB Serial Transport Tests`, `ECM Legacy Facade & Error Types`, `Bluetooth Classic Transport`, `ECM Facade Core`, `EcmTransport Contract Tests`, `Bluetooth Classic Transport Tests`, `ECM Protocol Enum`?**
  _High betweenness centrality (0.083) - this node is a cross-community bridge._
- **Are the 3 inferred relationships involving `PDU` (e.g. with `.testCorruptedChecksumRejected()` and `.testGetResponse()`) actually correct?**
  _`PDU` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `BuellTuneDestinations`, `Variables`, `RUNTIME_DATA` to the rest of the system?**
  _74 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `PDU Protocol Encoding` be split into smaller, more focused modules?**
  _Cohesion score 0.055176890619928594 - nodes in this community are weakly interconnected._