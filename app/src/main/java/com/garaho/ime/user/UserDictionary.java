package com.garaho.ime.user;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
 */
public final class UserDictionary implements UserWordSource {

    public static final String FILE_NAME = "user_dict.json";

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

    public synchronized void add(String pinyin, String word) {
        if (isBlank(pinyin) || isBlank(word)) {
            return;
        }
        String key = normalize(pinyin);
        LinkedHashSet<String> set = map.get(key);
        if (set == null) {
            set = new LinkedHashSet<>();
            map.put(key, set);
        }
        if (set.add(word.trim())) {
            persist();
        }
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

    private void load() {
        if (!file.exists()) {
            return;
        }
        try {
            String json = readUtf8(new FileInputStream(file));
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String p = o.optString("pinyin", "");
                String w = o.optString("word", "");
                if (!isBlank(p) && !isBlank(w)) {
                    map.computeIfAbsent(normalize(p), k -> new LinkedHashSet<String>()).add(w);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void persist() {
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
        try {
            FileOutputStream out = new FileOutputStream(file);
            try {
                out.write(arr.toString().getBytes("UTF-8"));
            } finally {
                out.close();
            }
        } catch (IOException ignored) {
        }
    }

    private static String normalize(String s) {
        return s.toLowerCase().replace("'", "").replace(" ", "").trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String readUtf8(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = in.read(tmp)) > 0) {
            buf.write(tmp, 0, n);
        }
        in.close();
        return buf.toString("UTF-8");
    }
}
