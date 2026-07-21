package com.garaho.ime.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Shared T9 digit-string segmenter (design doc §3.3.2 T9 拼音).
 *
 * <p>Converts a sequence of T9 digits into every valid pinyin-syllable split,
 * e.g. {@code "64426"} &rarr; {@code [[ni,hao], [mi,hao], [ni,ha,o], ...]}.
 * Used by both {@link T9PinyinEngine} (embedded dictionary) and
 * {@link RimeEngine} (feeds the segmented pinyin as a key sequence to
 * librime).
 */
public final class T9Segmenter {

    public static final int MAX_SEGMENTATIONS = 64;

    private static final Comparator<List<String>> BY_PHRASE_HIT = new Comparator<List<String>>() {
        @Override
        public int compare(List<String> a, List<String> b) {
            boolean aHit = PinyinDictionary.has(joinKey(a));
            boolean bHit = PinyinDictionary.has(joinKey(b));
            if (aHit != bHit) {
                return aHit ? -1 : 1;
            }
            if (a.size() != b.size()) {
                return Integer.compare(a.size(), b.size());
            }
            // Prefer the segmentation whose syllables have more dictionary
            // entries (a commonness proxy): "64" -> ni (7 entries) over mi (6),
            // so typing 64 surfaces 你 rather than alphabetically-first 米.
            int wa = dictWeight(a);
            int wb = dictWeight(b);
            if (wa != wb) {
                return Integer.compare(wb, wa);
            }
            return joinKey(a).compareTo(joinKey(b));
        }
    };

    private static int dictWeight(List<String> seg) {
        int w = 0;
        for (String s : seg) {
            w += PinyinDictionary.lookup(s).size();
        }
        return w;
    }

    private static String pickCommonSyllable(List<String> syls) {
        String best = syls.get(0);
        int bestCount = PinyinDictionary.lookup(best).size();
        for (String s : syls) {
            int c = PinyinDictionary.lookup(s).size();
            if (c > bestCount) {
                best = s;
                bestCount = c;
            }
        }
        return best;
    }

    private T9Segmenter() {
    }

    public static List<List<String>> segment(String digits) {
        List<List<String>> out = new ArrayList<>();
        if (digits == null || digits.isEmpty()) {
            return out;
        }
        segmentInto(digits, 0, new ArrayList<String>(), out);
        return out;
    }

    private static void segmentInto(String digits, int start, List<String> cur, List<List<String>> out) {
        if (out.size() >= MAX_SEGMENTATIONS) {
            return;
        }
        if (start == digits.length()) {
            out.add(new ArrayList<>(cur));
            return;
        }
        int maxLen = Math.min(6, digits.length() - start);
        for (int len = 1; len <= maxLen; len++) {
            String chunk = digits.substring(start, start + len);
            List<String> syls = PinyinSyllables.syllablesForT9(chunk);
            if (syls.isEmpty()) {
                continue;
            }
            for (String syl : syls) {
                cur.add(syl);
                segmentInto(digits, start + len, cur, out);
                cur.remove(cur.size() - 1);
                if (out.size() >= MAX_SEGMENTATIONS) {
                    return;
                }
            }
        }
    }

    /** @return the top-ranked segmentation's apostrophe-joined phrase (e.g. {@code "ni'hao"}). */
    public static String bestPhraseKey(String digits) {
        List<List<String>> segs = segment(digits);
        if (segs.isEmpty()) {
            return digits == null ? "" : digits;
        }
        return bestPhraseKey(segs);
    }

    public static String bestPhraseKey(List<List<String>> segs) {
        if (segs.isEmpty()) {
            return "";
        }
        List<List<String>> sorted = new ArrayList<>(segs);
        Collections.sort(sorted, BY_PHRASE_HIT);
        return joinKey(sorted.get(0));
    }

    /** @return the top-ranked segmentation itself (ordered). */
    public static List<String> bestSegmentation(String digits) {
        List<List<String>> segs = segment(digits);
        if (segs.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<String>> sorted = new ArrayList<>(segs);
        Collections.sort(sorted, BY_PHRASE_HIT);
        return sorted.get(0);
    }

    public static String joinKey(List<String> seg) {
        if (seg.isEmpty()) {
            return "";
        }
        if (seg.size() == 1) {
            return seg.get(0);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < seg.size(); i++) {
            if (i > 0) {
                sb.append('\'');
            }
            sb.append(seg.get(i));
        }
        return sb.toString();
    }

    /** Result of a best-effort (greedy leftmost) segmentation. */
    public static final class Segment {
        public final String phraseKey;
        public final String remainder;

        public Segment(String phraseKey, String remainder) {
            this.phraseKey = phraseKey;
            this.remainder = remainder;
        }
    }

    /**
     * Greedy leftmost-longest segmentation that tolerates an unsegmentable
     * tail. Returns the pinyin phrase for the longest segmentable prefix and
     * the leftover raw digits, so a T9 IME can show candidates for the valid
     * prefix while the user is still mid-syllable (e.g. {@code "789"} &rarr;
     * {@code phraseKey="pu", remainder="9"}) instead of dumping raw digits.
     *
     * At each position the longest syllable code is chosen; among syllables
     * sharing a code, the first one present in the dictionary is preferred.
     */
    public static Segment bestEffort(String digits) {
        if (digits == null || digits.isEmpty()) {
            return new Segment("", "");
        }
        StringBuilder phrase = new StringBuilder();
        int i = 0;
        while (i < digits.length()) {
            String chosen = null;
            int chosenLen = 0;
            int maxLen = Math.min(6, digits.length() - i);
            for (int len = maxLen; len >= 1; len--) {
                List<String> syls = PinyinSyllables.syllablesForT9(digits.substring(i, i + len));
                if (!syls.isEmpty()) {
                    chosen = pickCommonSyllable(syls);
                    chosenLen = len;
                    break;
                }
            }
            if (chosen == null) {
                break;
            }
            if (phrase.length() > 0) {
                phrase.append('\'');
            }
            phrase.append(chosen);
            i += chosenLen;
        }
        return new Segment(phrase.toString(), digits.substring(i));
    }

    /** Strip apostrophe separators so a phrase key can be fed to rime as letters. */
    public static String phraseKeyToLetters(String phraseKey) {
        if (phraseKey == null || phraseKey.isEmpty()) {
            return "";
        }
        return phraseKey.replace("'", "");
    }

    public static Comparator<List<String>> phraseHitComparator() {
        return BY_PHRASE_HIT;
    }
}
