package com.garaho.ime.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Splits a T9 digit string into the three-layer model used by the composing UI
 * (layer 1 = locked/determined prefix, layer 2 = options for the active tail
 * group, layer 3 = word candidates derived elsewhere).
 *
 * <p>{@link #segmentForLayer(String)} returns the determined leading syllables
 * as {@code prefix} and the trailing digit group as {@code tailDigits} with
 * {@code tailOptions} = every complete pinyin syllable that decodes to those
 * digits plus the single letters of the group's first key (for the
 * "still typing" case, e.g. {@code "24"} -> {@code [ai, bi, ci, a, b, c]}).
 */
public final class PinyinLayer {

    public static final class LayerSegment {
        public final List<String> prefix;
        public final String tailDigits;
        public final List<String> tailOptions;

        public LayerSegment(List<String> prefix, String tailDigits, List<String> tailOptions) {
            this.prefix = prefix;
            this.tailDigits = tailDigits;
            this.tailOptions = tailOptions;
        }
    }

    private PinyinLayer() {
    }

    public static LayerSegment segmentForLayer(String digits) {
        if (digits == null || digits.isEmpty()) {
            return new LayerSegment(Collections.<String>emptyList(), "", Collections.<String>emptyList());
        }
        List<String> prefix;
        String tailDigits;

        List<String> full = T9Segmenter.bestSegmentation(digits);
        if (!full.isEmpty()) {
            // Fully segmentable: everything except the last syllable is treated
            // as determined prefix; the last syllable is the active group.
            prefix = new ArrayList<>(full.subList(0, full.size() - 1));
            tailDigits = PinyinSyllables.t9Encode(full.get(full.size() - 1));
        } else {
            // Incomplete tail (e.g. trailing single key that can't close a
            // syllable): greedily segment the determined prefix and leave the
            // remainder as the active group.
            T9Segmenter.Segment best = T9Segmenter.bestEffort(digits);
            prefix = new ArrayList<>();
            String phraseKey = best.phraseKey;
            if (phraseKey != null && !phraseKey.isEmpty()) {
                Collections.addAll(prefix, phraseKey.split("'"));
            }
            tailDigits = best.remainder.isEmpty() ? digits : best.remainder;
        }

        return new LayerSegment(prefix, tailDigits, buildOptions(tailDigits));
    }

    private static List<String> buildOptions(String tailDigits) {
        if (tailDigits == null || tailDigits.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        // Complete syllables whose T9 code equals the whole tail group.
        for (String syl : PinyinSyllables.syllablesForT9(tailDigits)) {
            out.add(syl);
        }
        // Partial: the letters of the first key (the user may keep typing and
        // extend one of them into a longer syllable).
        int firstDigit = tailDigits.charAt(0) - '0';
        String group = MultiTapCore.group(firstDigit);
        if (group != null) {
            for (int i = 0; i < group.length(); i++) {
                out.add(String.valueOf(group.charAt(i)));
            }
        }
        return new ArrayList<>(out);
    }
}
