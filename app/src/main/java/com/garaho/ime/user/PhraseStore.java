package com.garaho.ime.user;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Canned-phrase store (design doc §2.4): quick-insert snippets grouped by
 * category (email / greeting / personal). File-backed JSON
 * ({@code filesDir/phrases.json}), cached in memory, process-wide singleton.
 *
 * <p>Persistence is atomic (temp file + rename) and corruption-aware (improvement
 * doc §5): a damaged file is moved aside to {@code phrases.json.corrupt} for
 * manual recovery and the store starts empty.
 */
public final class PhraseStore {

    public static final String FILE_NAME = "phrases.json";
    public static final String EXPORT_FILE_NAME = "WindIme_phrases.json";
    public static final int LABEL_MAX = 64;
    public static final int TEXT_MAX = 2000;
    public static final int MAX_ENTRIES = 1000;
    public static final int MAX_IMPORT_ENTRIES = 5000;

    public static final class Entry {
        public final String category;
        public final String label;
        public final String text;

        public Entry(String category, String label, String text) {
            this.category = category == null ? "" : category;
            this.label = label == null ? "" : label;
            this.text = text == null ? "" : text;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Entry)) return false;
            Entry entry = (Entry) other;
            return category.equals(entry.category) && text.equals(entry.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(category, text);
        }
    }

    private static volatile PhraseStore instance;

    private final File file;
    private final List<Entry> entries = new ArrayList<>();

    private PhraseStore(File file) {
        this.file = file;
        load();
    }

    public static synchronized PhraseStore get(Context context) {
        if (instance == null) {
            instance = new PhraseStore(new File(context.getFilesDir(), FILE_NAME));
        }
        return instance;
    }

    public static PhraseStore forFile(File file) {
        return new PhraseStore(file);
    }

    public synchronized List<Entry> entries() {
        return new ArrayList<>(entries);
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized StoreResult add(String category, String label, String text) {
        List<Entry> next = new ArrayList<>(entries);
        StoreResult r = validate(next, null, category, label, text);
        if (r != StoreResult.OK) {
            return r;
        }
        next.add(build(category, label, text));
        if (!persist(next)) {
            return StoreResult.IO_ERROR;
        }
        replaceEntries(next);
        return StoreResult.OK;
    }

    public synchronized StoreResult update(int index, String category, String label, String text) {
        if (index < 0 || index >= entries.size()) {
            return StoreResult.EMPTY;
        }
        List<Entry> current = new ArrayList<>(entries);
        Entry previous = current.get(index);
        List<Entry> next = new ArrayList<>(entries);
        StoreResult r = validate(next, previous, category, label, text);
        if (r != StoreResult.OK) {
            return r;
        }
        current.set(index, build(category, label, text));
        next = current;
        if (!persist(next)) {
            return StoreResult.IO_ERROR;
        }
        replaceEntries(next);
        return StoreResult.OK;
    }

    /** Validate an entry without counting {@code ignored} as a duplicate. */
    private StoreResult validate(List<Entry> target, Entry ignored,
                                 String category, String label, String text) {
        if (isBlank(text)) {
            return StoreResult.EMPTY;
        }
        String cat = trimTo(category);
        String lbl = trimTo(label);
        String txt = text.trim();
        if (lbl.length() > LABEL_MAX || txt.length() > TEXT_MAX) {
            return StoreResult.TOO_LONG;
        }
        Entry candidate = new Entry(cat, lbl, txt);
        if (target.size() >= MAX_ENTRIES && ignored == null) {
            return StoreResult.TOO_MANY;
        }
        if (target.contains(candidate) && !candidate.equals(ignored)) {
            return StoreResult.DUPLICATE;
        }
        return StoreResult.OK;
    }

    private Entry build(String category, String label, String text) {
        return new Entry(trimTo(category), trimTo(label), text.trim());
    }

    public synchronized boolean remove(int index) {
        if (index < 0 || index >= entries.size()) {
            return false;
        }
        List<Entry> next = new ArrayList<>(entries);
        next.remove(index);
        if (!persist(next)) {
            return false;
        }
        replaceEntries(next);
        return true;
    }

    public synchronized boolean clear() {
        if (entries.isEmpty()) {
            return true;
        }
        List<Entry> next = new ArrayList<>();
        if (!persist(next)) {
            return false;
        }
        entries.clear();
        return true;
    }

    /**
     * Append entries from {@code src}, merging with validation and de-duplication.
     *
     * @return number of newly added entries, or {@code -1} on parse failure.
     */
    public int importFrom(File src) {
        List<Entry> imported = parseImport(src);
        if (imported == null) {
            return -1;
        }
        return mergeImported(imported);
    }

    private static List<Entry> parseImport(File src) {
        if (src == null) {
            return null;
        }
        JSONArray arr;
        try {
            AtomicStore.recover(src);
            if (!src.exists()) {
                return null;
            }
            arr = new JSONArray(AtomicStore.readUtf8(src, AtomicStore.MAX_IMPORT_BYTES));
        } catch (Exception e) {
            return null;
        }
        if (arr.length() > MAX_IMPORT_ENTRIES) {
            return null;
        }
        List<Entry> imported = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                imported.add(new Entry(
                        trimTo(o.optString("category", "")),
                        trimTo(o.optString("label", "")),
                        o.optString("text", "")));
            } catch (Exception ignored) {
            }
        }
        return imported;
    }

    private synchronized int mergeImported(List<Entry> imported) {
        List<Entry> next = new ArrayList<>(entries);
        Set<Entry> known = new HashSet<>(entries);
        int added = 0;
        for (Entry entry : imported) {
            if (isBlank(entry.text) || entry.label.length() > LABEL_MAX
                    || entry.text.trim().length() > TEXT_MAX
                    || next.size() >= MAX_ENTRIES) {
                continue;
            }
            Entry candidate = build(entry.category, entry.label, entry.text);
            if (known.add(candidate)) {
                next.add(candidate);
                added++;
            }
        }
        if (added > 0 && !persist(next)) {
            return -1;
        }
        if (added > 0) {
            replaceEntries(next);
        }
        return added;
    }

    public synchronized boolean exportTo(File dest) {
        try {
            AtomicStore.writeAtomic(dest, toJson().toString().getBytes("UTF-8"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void load() {
        try {
            AtomicStore.recover(file);
            if (!file.exists()) {
                return;
            }
            JSONArray arr = new JSONArray(AtomicStore.readUtf8(file));
            List<Entry> loaded = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                loaded.add(new Entry(
                        trimTo(o.optString("category", "")),
                        trimTo(o.optString("label", "")),
                        o.optString("text", "")));
            }
            replaceEntries(loaded);
        } catch (Exception ex) {
            entries.clear();
            AtomicStore.backupCorrupt(file);
        }
    }

    private boolean persist(List<Entry> target) {
        try {
            AtomicStore.writeAtomic(file, toJson(target).toString().getBytes("UTF-8"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private JSONArray toJson() {
        return toJson(entries);
    }

    private static JSONArray toJson(List<Entry> target) {
        JSONArray arr = new JSONArray();
        for (Entry e : target) {
            JSONObject o = new JSONObject();
            try {
                o.put("category", e.category);
                o.put("label", e.label);
                o.put("text", e.text);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        return arr;
    }

    private void replaceEntries(List<Entry> next) {
        entries.clear();
        entries.addAll(next);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String trimTo(String s) {
        return s == null ? "" : s.trim();
    }
}
