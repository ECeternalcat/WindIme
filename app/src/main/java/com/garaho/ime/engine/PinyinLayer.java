package com.garaho.ime.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Layered T9 pinyin segmentation for the 3-row composing UI.
 *
 * <p>Splits a digit buffer into a locked syllable {@code prefix} plus an
 * adjustable {@code tail}, and enumerates the possible readings of that tail:
 * the complete pinyin syllables whose T9 code equals the tail digits, followed
 * by the single letters of the tail's first digit (the "keep typing" options).
 *
 * <p>Example: {@code "24"} &rarr; prefix [], tail "24", options {@code [ai, bi, ci,
 * a, b, c]}. The controller picks one tail option (via D-Pad) to form the
 * composing pinyin, and the word-candidate row updates in real time.
 */
public final class PinyinLayer {

    public static final class LayerSegment {
        public final List<String> prefix;
        public final String tailDigits;
        public final List<String> tailOptions;

        public LayerSegment(List<String> prefix, String tailDigits, List<String> tailOptions) {
            this.prefix = Collections.unmodifiableList(new ArrayList<>(prefix));
            this.tailDigits = tailDigits;
            this.tailOptions = Collections.unmodifiableList(new ArrayList<>(tailOptions));
        }
    }

    private PinyinLayer() {
    }

    public static LayerSegment segmentForLayer(String digits) {
        if (digits == null || digits.isEmpty()) {
            return new LayerSegment(Collections.<String>emptyList(), "", Collections.<String>emptyList());
        }
        T9Segmenter.Segment best = T9Segmenter.bestEffort(digits);
        List<String> segList = splitPhraseKey(best.phraseKey);
        String remainder = best.remainder;

        List<String> prefix;
        String tailDigits;
        if (!remainder.isEmpty()) {
            prefix = segList;
            tailDigits = remainder;
        } else if (segList.isEmpty()) {
            prefix = Collections.emptyList();
            tailDigits = digits;
        } else {
            String lastSyllable = segList.get(segList.size() - 1);
            prefix = segList.subList(0, segList.size() - 1);
            tailDigits = PinyinSyllables.t9Encode(lastSyllable);
        }
        return new LayerSegment(prefix, tailDigits, tailOptionsFor(tailDigits));
    }

    /** @return the apostrophe-joined phrase key for a prefix + a chosen tail reading. */
    public static String compose(List<String> prefix, String tailOption) {
        List<String> all = new ArrayList<>(prefix);
        if (tailOption != null && !tailOption.isEmpty()) {
            all.add(tailOption);
        }
        return T9Segmenter.joinKey(all);
    }

    private static List<String> tailOptionsFor(String tailDigits) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (tailDigits != null && !tailDigits.isEmpty()) {
            List<String> syls = new ArrayList<>(PinyinSyllables.syllablesForT9(tailDigits));
            out.addAll(syls);
            char firstDigit = tailDigits.charAt(0);
            if (firstDigit >= '2' && firstDigit <= '9') {
                String group = MultiTapCore.group(firstDigit - '0');
                if (group != null) {
                    for (int i = 0; i < group.length(); i++) {
                        out.add(String.valueOf(group.charAt(i)));
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static List<String> splitPhraseKey(String phraseKey) {
        List<String> out = new ArrayList<>();
        if (phraseKey == null || phraseKey.isEmpty()) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < phraseKey.length(); i++) {
            char c = phraseKey.charAt(i);
            if (c == '\'') {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }
}
