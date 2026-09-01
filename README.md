EcmDroid
--------

EcmDroid is an Android application to diagnose and configure Buell
Motorcycles with a DDFI(-2, -3) ECM.

Communication with the ECM is achieved through a Bluetooth-, BLE- or
USB-to-serial adapter connected to the motorcycle's diagnostic plug. The
plug is located underneath the seat (on XB-9/12 "S" models) or behind
the front mask (on XB-9/12 "R" models).

This [Article](https://ecmspy.com/btwireless2.shtml)
explains how to build a Bluetooth Serial Adapter. Pre-built
adapters are also offered by various vendors, e.g.
[buell-parts.com](https://buell-parts.com/Bluetooth-Adapter-Version-2).

Initial pairing of your Android device and the Bluetooth serial adapter
must be done using the Android Settings application (Wireless & Network).
Also,
* For Standard and P&A ECMs make sure that the Bluetooth serial adapter is set to 9600, 8N1,
No handshake.  This will be the vast majority of ECMs.
* For Factory Race ECMs make sure that the Bluetooth serial adapter is set to 19200, 8N1,
No handshake.

P&A Race ECMs can usually be identified by the ***printed*** RACE USE ONLY marking on the casing.  
Factory race ECMs can usually be identified by the ***engraved*** RACE USE ONLY marking on the casing.

Pairing is not required for BLE serial adapters.

Also checkout [ecmsim](https://github.com/ecmdroid/ecmsim) which can
be used for testing/debugging ecmdroid without a real ECM.

## Running the simulator

[ecmsim](https://github.com/ecmdroid/ecmsim) is vendored in this repo as a
pinned git submodule at `third_party/ecmsim`, with Gradle tasks to build and
run it — no manual Maven or repo hunting required.

```bash
git submodule update --init --recursive     # first time only (or clone with --recurse-submodules)

export JAVA_HOME=/path/to/jdk-21-or-newer   # ecmsim needs JDK 21+; this is separate from
                                             # (and may exceed) the JDK the Android app build uses
./gradlew ecmsimRun
```

`ecmsimRun` builds `third_party/ecmsim/target/ecmsim.jar` if needed
(`ecmsimBuild`, skipped when it's already up to date) and starts it against
the bundled `BUEIB` fixtures (`app/src/androidTest/resources/BUEIB.eeprom`,
`BUEIB_log.bin`) on TCP port `6280` — a different port than ecmsim's own
default of `6275`, so it won't collide with an instance you're already
running manually. Once it logs `Waiting for incoming connection on port
6280...`, point EcmDroid's TCP/IP connection at that port on the host
running the simulator.

Override the model, port, EEPROM dump, or log file with Gradle project
properties:

```bash
./gradlew ecmsimRun -PecmsimModel=BUE2D -PecmsimPort=6280 \
  -PecmsimXpr=app/src/androidTest/resources/BUE2D.eeprom
```

If your default `JAVA_HOME` isn't JDK 21+, pass
`-PecmsimJavaHome=/path/to/jdk21` (or set `ECMSIM_JAVA_HOME`) to point just
these two tasks at one without changing the JDK used for the rest of the
build. `./gradlew ecmsimBuild` builds the jar on its own; `--list` (via
`java -jar third_party/ecmsim/target/ecmsim.jar --list`) enumerates every
ECM model ecmsim knows how to simulate.

## Documentation

* [User Guide](docs/USER_GUIDE.md) — installing, connecting, and using every
  screen in the app.
* [Developer Guide](docs/DEVELOPER_GUIDE.md) — architecture, wire protocol,
  data model, build/test instructions.
