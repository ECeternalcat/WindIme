package com.garaho.ime.user;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * User dictionary store (design doc §2.3): pinyin &rarr; word entries the user
 * adds for names / jargon / words the bundled dictionary lacks.
 *
 * <p>File-backed JSON ({@code filesDir/user_dict.json}), cached in memory, and
 * process-wide singleton so the IME service and the settings activity share
 * one view (same process). Implements {@link UserWordSource} so pinyin engines
 * can prepend user words to their candidate lists.
 *
 * <p>Persistence is atomic (temp file + rename) and corruption-aware: a damaged
 * file is moved aside to {@code user_dict.json.corrupt} for manual recovery and
 * the store starts empty rather than being overwritten (improvement doc §5).
 */
public final class UserDictionary implements UserWordSource {

    public static final String FILE_NAME = "user_dict.json";
    public static final String EXPORT_FILE_NAME = "WindIme_user_dict.json";
    public static final int PINYIN_MAX = 64;
    public static final int WORD_MAX = 64;
    /** Maximum total entries to prevent unbounded memory growth. */
    public static final int MAX_ENTRIES = 5000;

    public static final class Entry {
        public final String pinyin;
        public final String word;

        public Entry(String pinyin, String word) {
            this.pinyin = pinyin;
            this.word = word;
        }
    }

    private static volatile UserDictionary instance;

    private final File file;
    private final Map<String, LinkedHashSet<String>> map = new LinkedHashMap<>();

    private UserDictionary(File file) {
        this.file = file;
        load();
    }

    public static synchronized UserDictionary get(Context context) {
        if (instance == null) {
            instance = new UserDictionary(new File(context.getFilesDir(), FILE_NAME));
        }
        return instance;
    }

    /** Test entry point: an isolated store backed by {@code file}. */
    public static UserDictionary forFile(File file) {
        return new UserDictionary(file);
    }

    @Override
    public synchronized List<String> lookup(String pinyin) {
        if (pinyin == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> set = map.get(normalize(pinyin));
        if (set == null || set.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(set);
        Collections.reverse(out);
        return out;
    }

    public synchronized StoreResult add(String pinyin, String word) {
        StoreResult r = addInternal(pinyin, word);
        if (r != StoreResult.OK) {
            return r;
        }
        return persist() ? StoreResult.OK : StoreResult.IO_ERROR;
    }

    private StoreResult addInternal(String pinyin, String word) {
        if (isBlank(pinyin) || isBlank(word)) {
            return StoreResult.EMPTY;
        }
        String p = pinyin.trim();
        String w = word.trim();
        if (p.length() > PINYIN_MAX || w.length() > WORD_MAX) {
            return StoreResult.TOO_LONG;
        }
        if (size() >= MAX_ENTRIES) {
            return StoreResult.TOO_MANY;
        }
        String key = normalize(p);
        LinkedHashSet<String> set = map.get(key);
        if (set != null && set.contains(w)) {
            return StoreResult.DUPLICATE;
        }
        if (set == null) {
            set = new LinkedHashSet<>();
            map.put(key, set);
        }
        set.add(w);
        return StoreResult.OK;
    }

    public synchronized boolean remove(String pinyin, String word) {
        LinkedHashSet<String> set = map.get(normalize(pinyin));
        if (set == null || !set.remove(word)) {
            return false;
        }
        if (set.isEmpty()) {
            map.remove(normalize(pinyin));
        }
        persist();
        return true;
    }

    public synchronized int size() {
        int n = 0;
        for (LinkedHashSet<String> set : map.values()) {
            n += set.size();
        }
        return n;
    }

    public synchronized List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, LinkedHashSet<String>> e : map.entrySet()) {
            for (String w : e.getValue()) {
                out.add(new Entry(e.getKey(), w));
            }
        }
        return out;
    }

    public synchronized void clear() {
        if (map.isEmpty()) {
            return;
        }
        map.clear();
        persist();
    }

    /**
     * Append entries from {@code src} (same JSON format as the store), merging
     * with validation and de-duplication.
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
                if (addInternal(o.optString("pinyin", ""), o.optString("word", "")) == StoreResult.OK) {
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

    /** Write the current entries to {@code dest} using the store's JSON format. */
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
                String p = o.optString("pinyin", "");
                String w = o.optString("word", "");
                if (!isBlank(p) && !isBlank(w)) {
                    map.computeIfAbsent(normalize(p), k -> new LinkedHashSet<String>()).add(w);
                }
            }
        } catch (Exception ex) {
            // Corrupt JSON: discard any partial state, preserve the original
            // aside for manual recovery, and start from an empty store.
            map.clear();
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
        for (Map.Entry<String, LinkedHashSet<String>> e : map.entrySet()) {
            for (String w : e.getValue()) {
                JSONObject o = new JSONObject();
                try {
                    o.put("pinyin", e.getKey());
                    o.put("word", w);
                    arr.put(o);
                } catch (Exception ignored) {
                }
            }
        }
        return arr;
    }

    private static String normalize(String s) {
        return s.toLowerCase().replace("'", "").replace(" ", "").trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
