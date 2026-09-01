# Repository Guidelines

## Project Overview

BuellTune is a native Android diagnostic and tuning app for Buell motorcycles running Zeeltronic/ecmspy-derived DDFI(-1,-2,-3) engine control modules (ECMs). It connects to the ECM over Bluetooth Classic, Bluetooth Low Energy (BLE), or USB-to-serial adapters to:

- Read live runtime data (RPM, TPS, CLT, fueling, timing, etc.)
- Read/write EEPROM configuration (calibration tables, scalars, bitfields)
- Read current/recent/stored error codes and clear them
- Trigger active tests (fuel pump, ignition coils, injectors, fan, exhaust valve, shift light, etc.)
- Record binary runtime logs and convert them to MegaLogViewer (`.msl`) format

License: GPL v3 (`LICENSE`). Package/applicationId: `biz.logicminds.buelltune`.

## Architecture & Data Flow

Mixed Java/Kotlin Android app: singleton hardware facade + background Service + fragment-based UI, with the domain/transport/service/data layers ported to Kotlin (coroutines/`Flow`, Room) while the legacy screens stay Java (`AsyncTask`, `android.app.Fragment`) per the compatibility-bridge convention below. No DI framework.

**Connect → Setup EEPROM → Poll runtime data → Log / Act**

1. **Connect**: `MainActivity` (`app/src/main/java/biz/logicminds/buelltune/activities/MainActivity.java`) drives device selection (paired Bluetooth, BLE, or USB via `UsbSerialPort`) and calls `ECM.connect(...)`.
2. **`ECM`** (`app/src/main/java/biz/logicminds/buelltune/ECM.java`) is a singleton facade abstracting the transport (BluetoothSocket / BLE `SerialSocket` / USB `UsbSerialPort` / TCP fallback via `PipedStreams`) and protocol (`STOCK` vs `FACTORY_RACE`, differing PDU ECM IDs `0x42`/`0x55`). Exposes `readRTData()`, `readErrors()`, `executeActiveTest()`, `readEEPROM()`, `burnEEPROM()`.
3. **`PDU`** (`app/src/main/java/biz/logicminds/buelltune/PDU.java`) encodes/decodes the wire protocol: SOH/EOH/SOT/EOT framing, XOR checksum, factory methods (`getRequest`, `setRequest`, `commandRequest`).
4. **EEPROM read/write**: `FetchTask`/`BurnTask` (`app/src/main/java/biz/logicminds/buelltune/task/`, `AsyncTask` subclasses of `ProgressDialogTask`) page through EEPROM via `PDU.getRequest`/`setRequest`; `EEPROM` (`EEPROM.java`) is a paginated byte array with dirty-page tracking, so burns can be optimized to only write changed pages.
5. **Continuous polling**: `EcmDroidService` (foreground Service) runs a `ReaderThread` that loops on `ECM.readRTData()` at a configurable interval (50–5000ms), broadcasts a `REALTIME_DATA` intent, and streams `[timestamp][ECM header][data]` records to a log file when recording.
6. **Variable definitions & scaling**: `Variable` (`Variable.java`) models a single runtime/EEPROM parameter (type, offset, scale/translate, format, unit) and does the raw-bytes → display-value conversion (`refreshValue()`). Definitions are looked up via `VariableProvider`/`BitSetProvider` interfaces, backed by `DatabaseVariableProvider`/`DatabaseBitSetProvider`, both caching results in a `HashMap`.
7. **Logging**: recorded binary logs can be converted to MegaLogViewer text format via `Bin2MslConverter` (`app/src/main/java/biz/logicminds/buelltune/util/`).

### Bundled ECM definitions database

The app ships a gzip-compressed SQLite database, `app/src/main/assets/buelltune.db.gz`, sourced from ecmspy.com MySQL dumps. `DBHelper` (`SQLiteOpenHelper`) extracts/decompresses it into the app's private DB directory on first launch or `DB_VERSION` bump, invoked from `EcmDroidApp.onCreate()`. Schema tables: `eeprom`, `pages`, `rtoffsets`, `eeoffsets`, `names`, `bits`. All variable/bitset/EEPROM-layout lookups (`DatabaseVariableProvider`, `DatabaseBitSetProvider`, `EEPROM.get()`) run raw SQL against this DB.

Updating the bundled DB (see `README.db`): apply an ecmspy.com MySQL backup → run `scripts/mysql2sqlite.sh` (awk-based MySQL-dump → SQLite converter) → strip column comments → build the sqlite file → gzip it into `app/src/main/assets/buelltune.db.gz` → bump `DB_VERSION` in `DBHelper.java` so devices re-extract it.

## Key Directories

| Path | Purpose |
|---|---|
| `app/src/main/java/biz/logicminds/buelltune/` | Core domain: `ECM`, `PDU`, `Variable`, `EEPROM`, `Error`, `DBHelper`, `VariableProvider`/`BitSetProvider` + DB-backed impls, `Constants` (500+ ECM variable name constants, `DataSource` enum), `Utils`, `ColorMap`, list adapters |
| `app/src/main/java/biz/logicminds/buelltune/activities/` | `MainActivity` (launcher, drawer nav, connection UI), `PrefsActivity` (settings), `AboutActivity` |
| `app/src/main/java/biz/logicminds/buelltune/fragments/` | One fragment per drawer tab: `MainFragment` (info), `TroubleCodeFragment`, `ActiveTestsFragment`, `DataChannelFragment`, `SetupFragment`, `LogFragment`, `EEPROMFragment`, `TorqueValuesFragment`, `CellEditorDialogFragment` |
| `app/src/main/java/biz/logicminds/buelltune/task/` | `AsyncTask` I/O: `ProgressDialogTask` (base, modal progress + orientation lock), `FetchTask`, `BurnTask` |
| `app/src/main/java/biz/logicminds/buelltune/util/` | `Bin2MslConverter` (binary log → MegaLogViewer `.msl`) |
| `app/src/main/java/de/kai_morich/simple_bluetooth_le_terminal/` | Vendored 3rd-party BLE UART abstraction (`SerialSocket`, `SerialListener`) supporting Nordic/TI/Microchip/Telit profiles |
| `app/src/main/assets/buelltune.db.gz` | Bundled gzipped SQLite DB of ECM variable/EEPROM definitions |
| `app/src/main/res/` | Layouts (`main.xml`, `log.xml`, `activity_main.xml`, …), `menu/main_drawer.xml`, `values/strings.xml`, `xml/ecm_setup.xml` (DDFI-1/2/3 variable defs), `xml/device_filter.xml` (USB VID/PID whitelist) |
| `app/src/androidTest/java/biz/logicminds/buelltune/` | Instrumented JUnit tests + `resources/` binary fixtures (`.eeprom`, `.bin`, `.msl`) |
| `scripts/` | `mysql2sqlite.sh` (DB conversion) |
| `gradle/libs.versions.toml` | Central version catalog for all Gradle deps/plugins |

## Development Commands

Gradle wrapper only; Gradle 9.4.1 pinned via `gradle/wrapper/gradle-wrapper.properties`.

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK (unsigned unless keystore.properties present)
./gradlew installDebug           # Build + install debug APK on connected device
./gradlew clean                  # Clean build outputs
./gradlew lint                   # Android Lint
./gradlew test                   # JVM unit tests (see Testing & QA)
./gradlew ecmsimIntegrationTest  # ecmsim-backed JVM integration suite (R16, R17) — see Testing & QA
./gradlew connectedAndroidTest   # Instrumented tests — REQUIRES a connected device/emulator
```

APK outputs: `app/build/outputs/apk/{debug,release}/`.

Release signing is optional and file-based: create a git-ignored `keystore.properties` at repo root with `keyAlias`, `keyPassword`, `storeFile`, `storePassword`; if absent, release build is unsigned.

Bump the shipped version by editing `versionCode`/`versionName` in `app/build.gradle.kts`.

Hardware-free ECM simulator: `third_party/ecmsim` is a pinned git submodule of `github.com/ecmdroid/ecmsim`. `./gradlew ecmsimBuild` builds its jar via its own Maven wrapper (requires a JDK 21+ `JAVA_HOME`, or pass `-PecmsimJavaHome=/path/to/jdk21`); `./gradlew ecmsimRun` builds it if needed and starts it against the bundled `BUEIB` fixtures on TCP port 6280 (`-PecmsimModel=`/`-PecmsimPort=`/`-PecmsimXpr=`/`-PecmsimLog=` override the defaults). `./gradlew ecmsimIntegrationTest` drives the same simulator from a JVM JUnit suite instead — see Testing & QA.

## Code Conventions & Common Patterns

- **Language**: mixed Java 8 and Kotlin (source/target compatibility `17`, jvmTarget follows `compileOptions`). The domain (`PDU`, `ECM`, `EEPROM`, `Variable`, …), transport (`transport/`), service (`service/`), and data (`data/`) layers are Kotlin; the legacy Activities/Fragments/`AsyncTask` tasks stay Java and consume the Kotlin layers through a compatibility bridge (`AppContainer`, `LegacyBroadcastBridge`) — do not port a legacy screen to Kotlin without discussion.
- **Naming**: classes PascalCase (`ECM`, `PDU`, `EEPROMFragment`); methods camelCase; constants UPPER_SNAKE_CASE; every class carries a `private static final String TAG` for `Log.d/i/w/e`.
- **Error handling**: exceptions, not return codes — `PDU.validate()` throws `ParseException` on malformed packets; caller-level failures surface via `Toast` or `AlertDialog`.
- **Threading**: legacy screens keep `AsyncTask`/plain `Thread`; the Kotlin service/transport layers use coroutines instead (`PollRecordLoop` on `Dispatchers.IO`, `EcmTransport.transact()` serialized per instance via `kotlinx.coroutines.sync.Mutex`). Patterns used:
  - `AsyncTask` subclasses of `ProgressDialogTask` (`FetchTask`, `BurnTask`) for ECM I/O with a modal progress dialog and screen-orientation freeze.
  - `PollRecordLoop` (Android-free, coroutine-based): replaces the legacy `EcmDroidService.ReaderThread`'s `Thread` + `synchronized`/`wait()`/`notify()` loop; hosted by `EcmService`.
  - UI updates communicated via `BroadcastReceiver` (`REALTIME_DATA`, `RECORDING_STARTED`/`RECORDING_STOPPED` intents), registered in `onResume()`/unregistered in `onPause()`.
  - `MainActivity`/`LogFragment` bind to `EcmService` via `ServiceConnection`.
- **Dependency management**: no DI framework. Singletons via static `getInstance(Context)` (e.g. `ECM.getInstance(...)`); fragments reach the host via `getActivity()`.
- **State**: `ECM` singleton holds connection/protocol/EEPROM state; `SharedPreferences` for user settings (protocol, storage location, log interval, keep-screen-on); `MainActivity` persists the active drawer fragment across rotation via `savedInstanceState`.
- **Database access**: the bundled ECM definitions DB is served through Room (`data/Entities.kt`, `data/Daos.kt`, `EcmDefinitionsDatabase`); `DatabaseVariableProvider`/`DatabaseBitSetProvider` query the generated DAOs instead of raw SQL, still wrapped in an in-memory `HashMap` cache — the DB is queried frequently during live polling. New JVM-only lookups (tests, `ecmsim` integration) go through the plain JDBC-SQLite `JdbcEcmDefinitionsProvider` in `app/src/test/.../integration/` instead, since Room needs a real Android `Context`.
- **Resource naming**: layouts named after the feature (`main.xml` ↔ `MainFragment`, `log.xml` ↔ `LogFragment`); menus `*_menu.xml`/`main_drawer.xml`; drawables descriptive (`ic_connected.xml`).
- **Vendored code**: `de.kai_morich.simple_bluetooth_le_terminal` is a third-party BLE package kept in-tree — treat as external, avoid unrelated edits.

## Important Files

- `app/src/main/java/biz/logicminds/buelltune/EcmDroidApp.java` — `Application` subclass; triggers DB extraction on startup.
- `app/src/main/java/biz/logicminds/buelltune/EcmDroidService.java` — foreground Service, entry point for continuous polling/recording.
- `app/src/main/java/biz/logicminds/buelltune/activities/MainActivity.java` — launcher `Activity`, drawer navigation host.
- `app/src/main/java/biz/logicminds/buelltune/ECM.java`, `PDU.java` — hardware/protocol core; read these before touching comms.
- `app/src/main/java/biz/logicminds/buelltune/DBHelper.java` — bundled-DB lifecycle; bump `DB_VERSION` when `buelltune.db.gz` changes.
- `app/src/main/java/biz/logicminds/buelltune/Constants.java` — canonical ECM variable name constants and `DataSource` enum.
- `app/src/main/AndroidManifest.xml` — component registration, permissions (`BLUETOOTH_SCAN`/`CONNECT`, `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`), USB intent filter.
- `app/build.gradle.kts`, `gradle/libs.versions.toml` — module config and version catalog; edit these together when bumping deps.
- `scripts/mysql2sqlite.sh` — DB-conversion tooling (see `README.db`).

## Runtime/Tooling Preferences

- Build exclusively through the Gradle wrapper (`./gradlew`, `gradlew.bat`) — do not rely on a globally installed Gradle; version is pinned (9.4.1) via `gradle/wrapper/gradle-wrapper.properties`.
- Android Gradle Plugin 9.2.1, `compileSdk` 36, `targetSdk` 36, `minSdk` 26 (Android 8.0+).
- Kotlin (AGP 9's built-in Kotlin, KSP, Compose compiler) coexists with the legacy Java/AndroidX-appcompat stack per the Language convention above; Compose is wired for the debug-only `BuellTuneDebugActivity` shell only, not any shipped legacy screen.
- Dependency versions are centralized in `gradle/libs.versions.toml` (version catalog) — add/bump deps there, reference via `libs.*` aliases in `app/build.gradle.kts`, not hardcoded coordinates.
- `local.properties`/`keystore.properties` are git-ignored — never commit secrets or local SDK paths.

## Testing & QA

- Both suites exist: `app/src/androidTest/java/biz/logicminds/buelltune/` (JUnit 4 + `AndroidJUnit4`, `connectedAndroidTest`) and `app/src/test/java/biz/logicminds/buelltune/` (JVM unit tests, `./gradlew test`, no device/emulator needed — covers the Kotlin domain/service layer). No CI pipeline (no GitHub Actions/GitLab CI/Jenkins config) — tests run only locally/manually.
- Files: `TestECM`, `TestPDU`, `TestBin2Msl`, `TestBitSetProvider`, `TestEEPROM`, `TestUtils`, `TestVariableProvider`. Naming convention: `Test<Component>.java`, methods prefixed `test`.
- Fixtures live in `app/src/androidTest/resources/` (`.eeprom` dumps, `.bin` runtime logs, `.msl` reference output) — reuse existing fixtures for parser/converter tests rather than inventing new binary formats.
- Run tests with a connected device or emulator:
  ```bash
  ./gradlew connectedAndroidTest
  ./gradlew connectedDebugAndroidTest
  ```
- `./gradlew lint` is available for static analysis; no enforced formatter/linter config was found — match surrounding code style exactly.
- When adding tests for new protocol/DB logic, follow the existing pattern: instrumented `Test<Component>` class under `androidTest`, using bundled `.eeprom`/`.bin` resources rather than mocks (no Robolectric/mocking framework is configured).
- **`ecmsim`-backed JVM integration suite** (R16, R17, AE5): `app/src/test/java/biz/logicminds/buelltune/integration/` drives the real `TcpTransport`/`ECM`/`PollRecordLoop` over TCP against a real local `ecmsim` process — connect/version handshake, EEPROM page fetch/write, realtime polling, an active-test trigger, a checksum-corrupted-frame rejection, and two connection-loss scenarios (killing the simulator process; closing the socket without killing it). `ECM`'s EEPROM-definitions/variable/bitset lookups are backed by a plain JDBC-SQLite implementation (`JdbcEcmDefinitionsProvider`/`JdbcVariableProvider`/`JdbcBitSetProvider` in that same package) reading the bundled `buelltune.db.gz`, never Room. Excluded from the default `test` task (starting a real simulator per test class is too slow for the inner dev loop) — run it with `./gradlew ecmsimIntegrationTest -PecmsimJavaHome=/path/to/jdk21` (same JDK-21+ requirement as `ecmsimBuild`/`ecmsimRun`; the task depends on `ecmsimBuild`, so the jar is built automatically if missing).

<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:970c3bf2 -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

**Architecture in one line:** issues live in a local Dolt DB; sync uses `refs/dolt/data` on your git remote; `.beads/issues.jsonl` is a passive export. See https://github.com/gastownhall/beads/blob/main/docs/SYNC_CONCEPTS.md for details and anti-patterns.

## Agent Context Profiles

The managed Beads block is task-tracking guidance, not permission to override repository, user, or orchestrator instructions.

- **Conservative (default)**: Use `bd` for task tracking. Do not run git commits, git pushes, or Dolt remote sync unless explicitly asked. At handoff, report changed files, validation, and suggested next commands.
- **Minimal**: Keep tool instruction files as pointers to `bd prime`; use the same conservative git policy unless active instructions say otherwise.
- **Team-maintainer**: Only when the repository explicitly opts in, agents may close beads, run quality gates, commit, and push as part of session close. A current "do not commit" or "do not push" instruction still wins.

## Session Completion

This protocol applies when ending a Beads implementation workflow. It is subordinate to explicit user, repository, and orchestrator instructions.

1. **File issues for remaining work** - Create beads for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **Handle git/sync by active profile**:
   ```bash
   # Conservative/minimal/default: report status and proposed commands; wait for approval.
   git status

   # Team-maintainer opt-in only, unless current instructions forbid it:
   git pull --rebase
   bd dolt push
   git push
   git status
   ```
5. **Hand off** - Summarize changes, validation, issue status, and any blocked sync/commit/push step

**Critical rules:**
- Explicit user or orchestrator instructions override this Beads block.
- Do not commit or push without clear authority from the active profile or the current user request.
- If a required sync or push is blocked, stop and report the exact command and error.
<!-- END BEADS INTEGRATION -->

<!-- BEGIN BEADS CODEX SETUP: generated by bd setup codex -->
## Beads Issue Tracker

Use Beads (`bd`) for durable task tracking in repositories that include it. Use the `beads` skill at `.agents/skills/beads/SKILL.md` (project install) or `~/.agents/skills/beads/SKILL.md` (global install) for Beads workflow guidance, then use the `bd` CLI for issue operations.

### Quick Reference

```bash
bd ready                # Find available work
bd show <id>            # View issue details
bd update <id> --claim  # Claim work
bd close <id>           # Complete work
bd prime                # Refresh Beads context
```

### Rules

- Use `bd` for all task tracking; do not create markdown TODO lists.
- Run `bd prime` when Beads context is missing or stale. Codex 0.129.0+ can load Beads context automatically through native hooks; use `/hooks` to inspect or toggle them.
- Keep persistent project memory in Beads via `bd remember`; do not create ad hoc memory files.

**Architecture in one line:** issues live in a local Dolt DB; sync uses `refs/dolt/data` on your git remote; `.beads/issues.jsonl` is a passive export. See https://github.com/gastownhall/beads/blob/main/docs/SYNC_CONCEPTS.md for details and anti-patterns.
<!-- END BEADS CODEX SETUP -->
