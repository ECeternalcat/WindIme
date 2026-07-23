package com.garaho.ime.user;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

    public static final class Entry {
        public final String category;
        public final String label;
        public final String text;

        public Entry(String category, String label, String text) {
            this.category = category == null ? "" : category;
            this.label = label == null ? "" : label;
            this.text = text == null ? "" : text;
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
        StoreResult r = validate(entries.size(), category, label, text);
        if (r != StoreResult.OK) {
            return r;
        }
        entries.add(build(category, label, text));
        return persist() ? StoreResult.OK : StoreResult.IO_ERROR;
    }

    public synchronized StoreResult update(int index, String category, String label, String text) {
        if (index < 0 || index >= entries.size()) {
            return StoreResult.EMPTY;
        }
        StoreResult r = validate(index, category, label, text);
        if (r != StoreResult.OK) {
            return r;
        }
        entries.set(index, build(category, label, text));
        return persist() ? StoreResult.OK : StoreResult.IO_ERROR;
    }

    /**
     * Validate an entry, ignoring the one at {@code ignoreIndex} so in-place
     * edit does not count itself as a duplicate. Does not mutate the store.
     * {@code ignoreIndex == size} means "no self to ignore" (used by add).
     */
    private StoreResult validate(int ignoreIndex, String category, String label, String text) {
        if (isBlank(text)) {
            return StoreResult.EMPTY;
        }
        String cat = trimTo(category);
        String lbl = trimTo(label);
        String txt = text.trim();
        if (lbl.length() > LABEL_MAX || txt.length() > TEXT_MAX) {
            return StoreResult.TOO_LONG;
        }
        if (containsDuplicate(ignoreIndex, cat, txt)) {
            return StoreResult.DUPLICATE;
        }
        return StoreResult.OK;
    }

    private Entry build(String category, String label, String text) {
        return new Entry(trimTo(category), trimTo(label), text.trim());
    }

    private boolean containsDuplicate(int ignoreIndex, String category, String text) {
        for (int i = 0; i < entries.size(); i++) {
            if (i == ignoreIndex) {
                continue;
            }
            Entry e = entries.get(i);
            if (e.category.equals(category) && e.text.equals(text)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void remove(int index) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        entries.remove(index);
        persist();
    }

    public synchronized void clear() {
        if (entries.isEmpty()) {
            return;
        }
        entries.clear();
        persist();
    }

    /**
     * Append entries from {@code src}, merging with validation and de-duplication.
     *
     * @return number of newly added entries, or {@code -1} on parse failure.
     */
    public synchronized int importFrom(File src) {
        if (src == null || !src.exists()) {
            return -1;
        }
        JSONArray arr;
        try {
            arr = new JSONArray(AtomicStore.readUtf8(src, AtomicStore.MAX_IMPORT_BYTES));
        } catch (Exception e) {
            return -1;
        }
        int added = 0;
        boolean changed = false;
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                String cat = trimTo(o.optString("category", ""));
                String lbl = trimTo(o.optString("label", ""));
                String txt = o.optString("text", "");
                if (validate(entries.size(), cat, lbl, txt) == StoreResult.OK) {
                    entries.add(new Entry(cat, lbl, txt.trim()));
                    added++;
                    changed = true;
                }
            } catch (Exception ignored) {
            }
        }
        if (changed && !persist()) {
            return -1;
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
        if (!file.exists()) {
            return;
        }
        try {
            JSONArray arr = new JSONArray(AtomicStore.readUtf8(file));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                entries.add(new Entry(
                        trimTo(o.optString("category", "")),
                        trimTo(o.optString("label", "")),
                        o.optString("text", "")));
            }
        } catch (Exception ex) {
            entries.clear();
            AtomicStore.backupCorrupt(file);
        }
    }

    private boolean persist() {
        try {
            AtomicStore.writeAtomic(file, toJson().toString().getBytes("UTF-8"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private JSONArray toJson() {
        JSONArray arr = new JSONArray();
        for (Entry e : entries) {
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

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String trimTo(String s) {
        return s == null ? "" : s.trim();
    }
}
