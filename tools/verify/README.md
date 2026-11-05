# Checking code without the Android SDK

These scripts check the app's logic without Gradle or an Android SDK. They exist for working in an environment where
the app cannot be built (a sandbox without the SDK), and they are quick enough to run on a laptop too.

```
tools/verify/setup.sh        # once: downloads the Kotlin compiler and an android.jar (API 35) into ~/.cache/gallery-verify (~110 MB)
tools/verify/typecheck.sh    # compiles the logic and data layers against stubs; "typecheck: clean" means no errors
tools/verify/test.sh         # runs the pure-Kotlin unit tests on the JVM; prints passed=N failed=M
```

Needs `bash`, `curl`, `unzip` and a JDK (17 or newer). Set `VERIFY_CACHE` to keep the downloads elsewhere.

## What they do

* `typecheck.sh` compiles `domain/`, the index database, the MediaStore data source, the scans, the CLIP code and the settings
  store against a real `android.jar`, kotlinx.coroutines and small stubs of the other libraries (`stubs/`: Hilt, Room, DataStore,
  WorkManager, ONNX Runtime as Java, ...). It finds a misspelled function, a missing import, a call that does not exist (such as
  `LongArray.mapNotNull`) or a type mismatch. Pass more files (relative to the repository root) to include them.
* `test.sh` compiles the pure sources and their tests with a stand-in for JUnit (`stubs/junit.kt`) and runs them by reflection.

## What they do not do

* Compose screens are not checked (Compose's compiler plugin and libraries are not available).
* Room's SQL is not validated (Room does that while building), and Hilt's generated wiring is not run.
* Behaviour that differs on a phone is not reproduced. Example: Android's regex engine is based on ICU and the JVM's is not,
  so an inline flag such as `(?U)` passes the tests and throws on a device.
* The real ONNX Runtime, WorkManager, DataStore and Room are not run; their stubs only describe the part of the API the app uses,
  written from the documentation. If a stub is wrong, the real build will say so.

So: a clean run means "no mistakes of the kinds above", and the Gradle build and a test on a phone remain the real checks.

## Keeping it working

When a checked file starts using a library that has no stub yet, the script reports `unresolved reference`. Add a small stub
under `stubs/libs/` (one package per file; Java sources go under `stubs/java/`) that declares only what the app calls.
