# WindIme Code Audit - 2026-07

## Scope

This audit covers the Android application source, manifest and Gradle configuration,
IME lifecycle, Java and native Rime integration, user data persistence, import/export,
key mapping, and the existing unit-test and lint baseline.

The prebuilt `trimelib/lib/armabi-v7/librime_jni.so` cannot be fully audited from this
repository because its reproducible native source and build chain are not present.

## Baseline

- Audit date: 2026-07-27
- Baseline commit: `e571552` (`UI improve 2`)
- Verification command: `./gradlew testDebugUnitTest lintDebug`
- Unit tests: 147 passed, 0 failed, 0 skipped
- Android lint: 1 fatal, 2 errors, 41 warnings; the build did not fail because lint
  enforcement was disabled

## Findings And Remediation

### A-01: Password fields do not use a private input path

- Severity: High
- Status: Fixed in working tree; device verification pending
- Evidence: `GarahoImeService.onStartInput()` records `inputType` but does not detect
  text, web, visible, or numeric password variations. Normal candidate generation,
  composing display, user dictionary merging, and Rime processing remain active.
- Impact: A password can appear in the IME candidate strip despite host masking, and
  sensitive input may enter prediction or user-learning state.
- Remediation: Detect all Android password variations, clear composition when entering
  or leaving a private field, bypass predictive engines, suppress candidates and inline
  composing, and directly commit keypad input.
- Acceptance: Unit tests cover all password variations and ordinary fields. Manual
  testing confirms the candidate strip never displays password content.

### A-02: User input is written to logcat

- Severity: High
- Status: Fixed
- Evidence: Commit text, selected user words, keypad digits, pinyin text, simulated key
  sequences, and candidate content are included in `Log` messages.
- Impact: Passwords, messages, addresses, and other input can be exposed through device
  diagnostics, vendor logging, ADB, or a log collection pipeline.
- Remediation: Remove content-bearing logs. Retain only non-sensitive state, counts,
  result codes, and session identifiers.
- Acceptance: A source scan finds no log statement containing committed, composing,
  candidate, digit, pinyin, phrase, or user-word content.

### A-03: API 21-23 crash in user dictionary loading

- Severity: High
- Status: Fixed
- Evidence: `UserDictionary.load()` calls `Map.computeIfAbsent`, which requires API 24,
  while `minSdk` is 21 and core library desugaring is not enabled.
- Impact: Loading an existing user dictionary can throw `NoSuchMethodError` on Android
  5.x or 6.0 and prevent the IME from starting.
- Remediation: Replace the call with API-21-compatible `get`/`put` operations.
- Acceptance: Android lint has no `NewApi` error and user dictionary tests pass.

### A-04: Rime startup state can expose an engine before schema deployment is ready

- Severity: High
- Status: Fixed; native device verification pending
- Evidence: `startedInProcess` becomes true immediately after native startup. If the
  Java deployment wait is interrupted during service recreation, the next service can
  reattach and publish the engine without completing `awaitSchema()`.
- Impact: JNI calls can race native maintenance, producing false-ready state, invalid
  candidates, a native crash, or database damage.
- Remediation: Track native startup and schema readiness separately. Reattachment must
  wait for or prove schema readiness before publishing the engine.
- Acceptance: Lifecycle tests cover `DEPLOYING`, `READY`, interruption, and reattachment.

### A-05: Direct Boot declaration does not match storage usage

- Severity: Medium
- Status: Fixed
- Evidence: The application is direct-boot-aware but immediately accesses default
  credential-protected files and preferences.
- Impact: Before first unlock on an FBE device, IME initialization can fail or repeatedly
  deploy data.
- Remediation: Remove the declaration unless a separate device-protected, non-sensitive
  startup mode is implemented.
- Acceptance: Manifest no longer advertises unsupported Direct Boot behavior.

### A-06: Failed user-data writes leave mutated in-memory state

- Severity: Medium
- Status: Fixed
- Evidence: User dictionary and phrase operations mutate collections before persistence;
  failures are not rolled back. Remove and clear operations ignore write failures.
- Impact: The UI and current process report data that is lost after restart, and later
  successful writes can unexpectedly persist a previously failed operation.
- Remediation: Use copy-on-write or rollback for every mutation and return storage errors
  to callers.
- Acceptance: Failure-injection tests verify memory and disk remain at the last committed
  state after add, update, remove, clear, and import failures.

### A-07: Phrase import is unbounded and has quadratic duplicate detection

- Severity: Medium
- Status: Fixed; device UI responsiveness verification pending
- Evidence: The import byte limit does not limit JSON entry count; each imported entry
  linearly scans the accumulated list, and import is invoked from the UI thread.
- Impact: A valid, compact import file can trigger high memory usage or an ANR on target
  low-end devices.
- Remediation: Enforce entry limits, use set-based duplicate detection, and perform file
  parsing and validation off the UI thread.
- Acceptance: Tests reject over-limit imports and validate linear-time deduplication.

### A-08: Keymap replacement deletes the old file before rename

- Severity: Medium
- Status: Fixed; filesystem/device verification pending
- Evidence: `KeyMapper.saveUserSlot()` deletes the target before renaming its temporary
  replacement.
- Impact: Process death, power loss, or rename failure can permanently remove the last
  valid keymap.
- Remediation: Use the existing atomic-storage mechanism or `AtomicFile` without deleting
  the only valid copy first.
- Acceptance: Failure-injection tests preserve the old keymap when replacement fails.

### A-09: Lint errors and release lint are not enforced

- Severity: Medium
- Status: Fixed
- Evidence: `abortOnError false` and `checkReleaseBuilds false` allow fatal and error lint
  findings to pass the build.
- Impact: Confirmed compatibility and correctness regressions can ship unnoticed.
- Remediation: Resolve blocking findings, enable lint enforcement, and baseline only
  reviewed non-blocking compatibility warnings.
- Acceptance: Debug and release lint complete with enforcement enabled.

### A-10: User data exports are persistent plaintext files

- Severity: Medium
- Status: Deferred product change
- Evidence: User words and phrases are exported as JSON under app-specific external
  storage.
- Impact: On older/vendor Android systems, ADB, backup software, or storage-capable apps
  can obtain names, addresses, terms, and saved phrases.
- Remediation: Use Storage Access Framework for explicit user-selected exports, explain
  plaintext risk, and use authenticated encryption for automatic backup.
- Acceptance: No implicit persistent export is created in external storage.

### A-11: Rime data marker failure is reported as successful extraction

- Severity: Low
- Status: Fixed
- Evidence: `RimeData.writeMarker()` swallows `IOException` and `ensureExtracted()` still
  returns success.
- Impact: The full bundled data may be deleted and extracted again on every startup.
- Remediation: Propagate marker failure and only report success after all data and marker
  writes complete.

### A-12: Pinyin normalization depends on the device locale

- Severity: Low
- Status: Fixed
- Evidence: `UserDictionary.normalize()` calls `toLowerCase()` without a locale.
- Impact: Dictionary keys can be inconsistent under locales with special casing rules.
- Remediation: Use `Locale.ROOT`.

## Recommended Order

1. A-01, A-02, A-03: privacy and deterministic compatibility failure.
2. A-04, A-05: native and service lifecycle correctness.
3. A-06, A-07, A-08, A-11, A-12: persistence and data integrity.
4. A-09: enforce the repaired baseline.
5. A-10: user-facing export redesign and platform migration.

## Residual Validation

The following require device or instrumentation testing and cannot be established by JVM
unit tests alone:

- Password fields across text, web, visible, and numeric variations.
- Service recreation while native Rime is compiling a schema.
- First-boot/first-unlock behavior on an FBE device.
- Low-storage, process-death, and power-loss persistence behavior.
- JNI memory and thread safety of the prebuilt native library.

## Remediation Result - 2026-07-28

The first remediation pass is complete. A strict review of the initial changes found and
closed additional risks in backup-only crash recovery, legacy over-limit data migration,
same-target concurrent writes, private multi-tap cursor handling, and phrase-import lock
duration.

Completed findings: A-01 through A-09, A-11, and A-12. A-10 remains a user-facing export
workflow and encryption design change. A-13 remains a separate target-SDK migration that
requires regression testing on the supported vendor flip phones.

Implemented controls include:

- Password fields and `IME_FLAG_NO_PERSONALIZED_LEARNING` fields use a private path with
  no prediction, candidates, user words, Rime learning, phrases, or content logging.
- Text passwords retain isolated ASCII multi-tap input; numeric private fields commit
  digits directly. External cursor movement invalidates an active replacement cycle.
- Native Rime process state distinguishes deployment from schema readiness, including
  service recreation while deployment is running.
- User dictionary, phrase, and keymap writes are copy-on-write and atomically replaced.
  Backup-only crash states are recovered before existence checks, legacy data is not
  silently truncated, and same-target writes are serialized process-wide.
- Phrase import has byte and entry limits, parses outside the store monitor, performs
  set-based duplicate checks, and runs through a lifecycle-guarded single worker.
- Direct Boot advertising was removed because no device-protected startup mode exists.
- Debug and release lint now fail on errors. `ExpiredTargetSdkVersion` is the sole explicit
  exception and is tracked by A-13.

Final verification:

- `./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease`
- 175 unit tests passed, 0 failed, 0 skipped
- Debug lint: no Fatal or Error findings
- Release lint: no Fatal or Error findings
- Debug and release APK assembly: successful
- Source scan: no input-content log patterns, `Map.computeIfAbsent`, or Direct Boot
  declaration remains
- `git diff --check`: clean
