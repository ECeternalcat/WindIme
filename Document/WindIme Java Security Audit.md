# WindIme Java Security Audit

**Date**: 2026-07-24  
**Scope**: All Java source files under `app/src/main/java/` (70 files)  
**Focus**: Potential vulnerabilities, thread safety, resource leaks, input validation

---

## Summary

| Severity | Count |
|----------|-------|
| High     | 3     |
| Medium   | 5     |
| Low      | 5     |

Overall code quality is solid. Core safety designs (atomic writes, JNI exception isolation,
lifecycle gating, safe-escape combo) are well implemented. Primary risks concentrate on
**unbounded file import** and **background thread holding Service reference**.

---

## High Severity

### H-1. Import file without size limit — potential OOM DoS

**Files**: `UserDictionary.java` L156-182, `PhraseStore.java` L149-179

`importFrom(File)` calls `AtomicStore.readUtf8(src)` which reads the entire file into
memory with **no file-size cap**. A malicious or erroneous import file (hundreds of MB)
causes an immediate OutOfMemoryError crash.

```java
arr = new JSONArray(AtomicStore.readUtf8(src)); // no size check
```

**Fix**: Check `src.length()` before reading; reject files exceeding a reasonable limit
(e.g. 10 MB).

---

### H-2. User dictionary has no total entry cap — memory exhaustion

**File**: `UserDictionary.java` L90-110

`addInternal()` limits individual pinyin/word length (64 chars) but imposes **no limit on
total entry count**. Via import or repeated additions the `map` grows unboundedly.

**Fix**: Add a `MAX_ENTRIES` constant (e.g. 5000) and check in `addInternal()`.

---

### H-3. Service memory leak — Rime init thread holds Service reference

**File**: `GarahoImeService.java` L234-298

The anonymous `Runnable` in `prepareRimeInBackground()` implicitly captures
`GarahoImeService.this`. `awaitSchema()` timeout is **30 minutes**. If the Service is
destroyed during that window, the entire Service (including all UI references) cannot be
GC'd until the thread finishes.

**Fix**: Capture a `WeakReference<GarahoImeService>` in the Runnable; check
`ref.get() == null || destroyed` at every checkpoint inside the thread.

---

## Medium Severity

### M-1. RimeEngine candidate index mismatch

**File**: `RimeEngine.java` L197-229

`selectCandidate()` operates on the merged list (user words + native candidates). When
the chosen word is not in `nativeCandidates` (`nativeIndex < 0`), it commits directly
without Rime confirmation, potentially leaving Rime's internal composition state stale.

**Fix**: Always call `Rime.clearRimeComposition()` before the user-word fast path, and
log the divergence for diagnostics.

---

### M-2. MultiTap engine Handler callback leak

**Files**: `ChineseMultiTapEngine.java` L24-44, `EnglishMultiTapEngine.java` L22-35

`timeoutRunnable` holds an implicit reference to the engine. If the Service is destroyed
while a `postDelayed` callback is pending (up to 1000 ms), the engine and its listener
(the Service) are briefly leaked.

**Fix**: Ensure `GarahoImeService.onDestroy()` calls `reset()` on all engines (including
multi-tap engines) to remove pending callbacks.

---

### M-3. AtomicStore.writeAtomic() TOCTOU race

**File**: `AtomicStore.java` L49-57

```java
if (!tmp.renameTo(target)) {
    if (target.exists() && !target.delete()) { ... }  // check
    if (!tmp.renameTo(target)) { ... }                 // act
}
```

Between `exists()` → `delete()` → `renameTo()` there is no atomicity guarantee. Risk is
negligible in Android's private directory but would matter if external storage is used.

**Fix**: Use `java.nio.file.Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` on API 26+,
fall back to current logic on older APIs.

---

### M-4. Missing runtime permission check — READ_EXTERNAL_STORAGE

**File**: `AndroidManifest.xml` L5

The manifest declares `READ_EXTERNAL_STORAGE` (dangerous level) but no runtime
`requestPermissions()` call exists. On Android 6-9 devices, import/export operations that
access shared storage will throw `SecurityException`.

**Fix**: Guard import/export with a permission check; request at runtime or migrate to
SAF (Storage Access Framework) / MediaStore.

---

### M-5. KeyMapConfig.fromJson() lacks numeric range validation

**File**: `KeyMapConfig.java` L40-57

`scanCode` and `keycode` are read via `optInt()` with no range validation. A crafted
config file could inject extreme values causing unexpected key-mapping behavior.

**Fix**: Clamp or reject values outside `[0, 65535]`.

---

## Low Severity

### L-1. AtomicStore.readUtf8() not using try-with-resources

**File**: `AtomicStore.java` L60-73

Manual try-finally is functionally correct but less resilient to refactoring errors.

---

### L-2. writeAtomic() does not fsync the parent directory

**File**: `AtomicStore.java` L28-58

File content is fsync'd but the directory entry after `renameTo()` is not. In extreme
power-loss scenarios the file could be lost. Negligible for IME user data.

---

### L-3. SettingsActivity exported=true without permission guard

**File**: `AndroidManifest.xml` L30-33

Any third-party app can launch the settings page. Low risk since settings only affect
the IME itself, but could be used for social-engineering (e.g. tricking users into
changing key mappings).

---

### L-4. EnglishDictionary linear scan

**File**: `EnglishDictionary.java` L217-243

`matches()` performs O(n) prefix scan over all entries. Current ~600 words pose no issue;
future dictionary expansion may need a Trie.

---

### L-5. RimeData.copyFile() not using try-with-resources

**File**: `RimeData.java` L132-148

Same style concern as L-1.

---

## Positive Security Design

| Design | Effect |
|--------|--------|
| `android:allowBackup="false"` | Prevents ADB backup leaking user dictionary |
| IME Service bound with `BIND_INPUT_METHOD` | Prevents malicious apps binding the IME |
| Atomic write (temp + rename + fsync) | Crash-safe persistence |
| Corrupt-file auto-backup (`.corrupt`) | Data recoverability |
| `InputAction.safeValueOf()` | Prevents crash from illegal enum in JSON |
| `RimeLifecycle` single-slot guard | Prevents duplicate native Rime init |
| `InputEventGate` lifecycle gate | Prevents key handling after Service destroy |
| Safe-escape combo (Backspace+# 5s) | Recovery from broken key mappings |
| All JNI calls wrapped in try-catch Throwable | Prevents native crash propagation |

---

## Fix Priority

1. **Immediate**: H-1, H-2 (exploitable via malicious file)
2. **Soon**: H-3, M-4 (stability / crash on real devices)
3. **Planned**: M-1, M-2, M-3, M-5 (edge conditions)
4. **Optional**: L-1 through L-5 (code quality)
