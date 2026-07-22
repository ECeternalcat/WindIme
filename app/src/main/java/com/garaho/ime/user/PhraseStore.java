package com.garaho.ime.user;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Canned-phrase store (design doc §2.4): quick-insert snippets grouped by
 * category (email / greeting / personal). File-backed JSON
 * ({@code filesDir/phrases.json}), cached in memory, process-wide singleton.
 */
public final class PhraseStore {

    public static final String FILE_NAME = "phrases.json";

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

    public synchronized void add(String category, String label, String text) {
        if (isBlank(label) && isBlank(text)) {
            return;
        }
        entries.add(new Entry(category, label.trim(), text.trim()));
        persist();
    }

    public synchronized void update(int index, String category, String label, String text) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        entries.set(index, new Entry(category, label.trim(), text.trim()));
        persist();
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

    private void load() {
        if (!file.exists()) {
            return;
        }
        try {
            JSONArray arr = new JSONArray(readUtf8(file));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                entries.add(new Entry(
                        o.optString("category", ""),
                        o.optString("label", ""),
                        o.optString("text", "")));
            }
        } catch (Exception ignored) {
        }
    }

    private void persist() {
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

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String readUtf8(File f) throws IOException {
        InputStream in = new java.io.FileInputStream(f);
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
