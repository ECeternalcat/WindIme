package com.garaho.ime.user;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Pinyin &rarr; user-word lookup that the pinyin engines consult to surface
 * user-defined words (design doc §2.3).  An engine chooses whether these
 * entries are prepended (Java fallback) or appended (native Rime priority).
 */
public interface UserWordSource {

    /**
     * @param pinyin the engine's current pinyin phrase (e.g. {@code "ni'hao"}
     *               or {@code "ni"}); normalization is the source's concern.
     * @return user-defined words matching that pinyin, most-recent-first.
     */
    List<String> lookup(String pinyin);

    /**
     * Prepend any user words for {@code pinyin} ahead of {@code base},
     * de-duplicating. This is kept for the Java fallback engines.
     */
    static List<String> merge(String pinyin, List<String> base, UserWordSource src) {
        if (src == null) {
            return base;
        }
        List<String> uw = src.lookup(pinyin);
        if (uw.isEmpty()) {
            return base;
        }
        LinkedHashSet<String> out = new LinkedHashSet<>(uw);
        out.addAll(base);
        return new ArrayList<>(out);
    }

    /**
     * Append any user words after {@code base}, de-duplicating. The Rime
     * engine uses this ordering so native Rime candidates remain first and
     * user-added words are offered afterwards.
     */
    static List<String> append(String pinyin, List<String> base, UserWordSource src) {
        if (src == null) {
            return base;
        }
        List<String> uw = src.lookup(pinyin);
        if (uw.isEmpty()) {
            return base;
        }
        LinkedHashSet<String> out = new LinkedHashSet<>(base);
        out.addAll(uw);
        return new ArrayList<>(out);
    }
}
