package com.garaho.ime.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure state model for layered T9 pinyin composition. */
public final class PinyinSession {

    private final List<String> lockedSyllables = new ArrayList<>();
    private final StringBuilder tailDigits = new StringBuilder();

    private PinyinLayer.LayerSegment layer = PinyinLayer.segmentForLayer("");
    private int selectedIndex = -1;
    private boolean confirmed;
    private boolean partialSelectionPinned;
    private boolean completeSelectionPinned;
    private int leadingSelectedIndex = -1;

    // Loop sound-selection mode: the full digit buffer is segmented into N
    // fixed positions and the user walks position-by-position picking a reading
    // for each. Segmentation is fixed; only the reading at each position is
    // adjustable. The lockedSyllables/tail model is ignored while loopMode is on.
    private boolean loopMode;
    private int loopEditPosition;
    private List<String> loopDigitGroups = new ArrayList<>();
    private List<String> loopReadings = new ArrayList<>();

    public boolean processDigit(int digit) {
        if (digit < 2 || digit > 9) {
            return false;
        }
        // A complete syllable the user actively selected (via LEFT/RIGHT) is
        // locked as soon as they continue typing, so multi-syllable input like
        // wo|ai|ni works without an explicit OK. Auto-default selections are
        // not pinned, so multi-digit syllables such as 426->hao still form.
        if (confirmed || completeSelectionPinned) {
            lockCurrentSelection();
        }
        String preferredPrefix = partialSelectionPinned ? selectedOption() : null;
        tailDigits.append((char) ('0' + digit));
        recomputeWithPrefix(preferredPrefix);
        if (loopMode) {
            rebuildLoopState();
        }
        return true;
    }

    public boolean backspace() {
        if (confirmed) {
            confirmed = false;
            return true;
        }
        if (tailDigits.length() > 0) {
            tailDigits.deleteCharAt(tailDigits.length() - 1);
            recompute(null);
            if (loopMode) {
                rebuildLoopState();
            }
            return true;
        }
        if (!lockedSyllables.isEmpty()) {
            String restored = lockedSyllables.remove(lockedSyllables.size() - 1);
            tailDigits.append(PinyinSyllables.t9Encode(restored));
            recompute(restored);
            if (loopMode) {
                rebuildLoopState();
            }
            return true;
        }
        return false;
    }

    public boolean preview(int index) {
        if (loopMode) {
            List<String> opts = loopOptionsForCurrent();
            if (index < 0 || index >= opts.size()) {
                return false;
            }
            loopReadings.set(loopEditPosition, opts.get(index));
            return true;
        }
        if (index < 0 || index >= getOptions().size()) {
            return false;
        }
        if (!layer.prefix.isEmpty()) {
            leadingSelectedIndex = index;
            return true;
        }
        selectedIndex = index;
        confirmed = false;
        boolean complete = isCompleteOption(selectedOption());
        partialSelectionPinned = !complete;
        completeSelectionPinned = complete;
        return true;
    }

    /**
     * Loop mode: lock the chosen reading at the current position, then advance
     * to the next position, wrapping back to position 0 after the last so the
     * user can review the whole phrase ("循环选音").
     */
    public boolean confirmAndAdvance(int index) {
        if (!loopMode) {
            return false;
        }
        if (!preview(index)) {
            return false;
        }
        int n = loopDigitGroups.size();
        if (n > 0) {
            loopEditPosition = (loopEditPosition + 1) % n;
        }
        return true;
    }

    public boolean confirm(int index) {
        if (!preview(index)) {
            return false;
        }
        if (!layer.prefix.isEmpty()) {
            String option = getOptions().get(leadingSelectedIndex);
            boolean valid = PinyinSyllables.isSyllable(option)
                    && PinyinSyllables.t9Encode(option)
                    .equals(PinyinSyllables.t9Encode(layer.prefix.get(0)));
            if (valid) {
                // Pin the leading choice so the next typed digit locks it via
                // lockCurrentSelection()/resolvedPrefix() - mirroring the tail
                // confirm path. Without this, recomputeWithPrefix() wipes
                // leadingSelectedIndex and the user's reading silently falls
                // back to the segmenter default (xie instead of zhe).
                confirmed = true;
                partialSelectionPinned = false;
            }
            return valid;
        }
        String option = selectedOption();
        if (!PinyinSyllables.isSyllable(option)
                || !PinyinSyllables.t9Encode(option).equals(layer.tailDigits)) {
            return false;
        }
        confirmed = true;
        partialSelectionPinned = false;
        return true;
    }

    public void reset() {
        lockedSyllables.clear();
        tailDigits.setLength(0);
        layer = PinyinLayer.segmentForLayer("");
        selectedIndex = -1;
        confirmed = false;
        partialSelectionPinned = false;
        completeSelectionPinned = false;
        leadingSelectedIndex = -1;
        loopEditPosition = 0;
        loopDigitGroups.clear();
        loopReadings.clear();
    }

    public List<String> getOptions() {
        if (loopMode) {
            return loopOptionsForCurrent();
        }
        if (!layer.prefix.isEmpty()) {
            return PinyinLayer.optionsForDigits(
                    PinyinSyllables.t9Encode(layer.prefix.get(0)));
        }
        return layer.tailOptions;
    }

    public int getSelectedIndex() {
        if (loopMode) {
            List<String> opts = loopOptionsForCurrent();
            String current = loopEditPosition >= 0 && loopEditPosition < loopReadings.size()
                    ? loopReadings.get(loopEditPosition) : "";
            int idx = opts.indexOf(current);
            return idx >= 0 ? idx : (opts.isEmpty() ? -1 : 0);
        }
        if (!layer.prefix.isEmpty()) {
            return leadingSelectedIndex >= 0 ? leadingSelectedIndex : 0;
        }
        return selectedIndex;
    }

    public String getPhraseKey() {
        if (loopMode) {
            return T9Segmenter.joinKey(loopReadings);
        }
        List<String> parts = new ArrayList<>(lockedSyllables);
        parts.addAll(resolvedPrefix());
        String option = selectedOption();
        if (isCompleteOption(option)) {
            parts.add(option);
        }
        return T9Segmenter.joinKey(parts);
    }

    public String getComposing() {
        if (loopMode) {
            return T9Segmenter.joinKey(loopReadings);
        }
        String phrase = getPhraseKey();
        String option = selectedOption();
        if (!isCompleteOption(option)) {
            List<String> parts = new ArrayList<>(lockedSyllables);
            parts.addAll(resolvedPrefix());
            if (!option.isEmpty()) {
                parts.add(option);
            }
            phrase = T9Segmenter.joinKey(parts);
        }
        if (!phrase.isEmpty()) {
            return phrase;
        }
        return getDigits();
    }

    public String getDigits() {
        StringBuilder out = new StringBuilder();
        for (String syllable : lockedSyllables) {
            out.append(PinyinSyllables.t9Encode(syllable));
        }
        out.append(tailDigits);
        return out.toString();
    }

    public boolean isEmpty() {
        return lockedSyllables.isEmpty() && tailDigits.length() == 0;
    }

    public List<String> getLockedSyllables() {
        return Collections.unmodifiableList(lockedSyllables);
    }

    private void lockCurrentSelection() {
        lockedSyllables.addAll(resolvedPrefix());
        String option = selectedOption();
        if (isCompleteOption(option)) {
            lockedSyllables.add(option);
            tailDigits.setLength(0);
            layer = PinyinLayer.segmentForLayer("");
            selectedIndex = -1;
        } else {
            // Mid-syllable tail (e.g. partial letters while the user confirmed
            // the leading reading): lock only the prefix and keep the tail
            // digits so the current syllable can still be finished. The legacy
            // paths never reach this branch - confirmed/completeSelectionPinned
            // guarantee a complete option - it only guards the leading-prefix
            // confirm case.
            layer = PinyinLayer.segmentForLayer(tailDigits.toString());
            selectedIndex = preferredIndex(null);
        }
        confirmed = false;
        partialSelectionPinned = false;
        completeSelectionPinned = false;
        leadingSelectedIndex = -1;
    }

    private void recompute(String preferredOption) {
        layer = PinyinLayer.segmentForLayer(tailDigits.toString());
        selectedIndex = preferredIndex(preferredOption);
        confirmed = false;
        partialSelectionPinned = false;
        completeSelectionPinned = false;
        leadingSelectedIndex = -1;
    }

    private void recomputeWithPrefix(String preferredPrefix) {
        layer = PinyinLayer.segmentForLayer(tailDigits.toString());
        completeSelectionPinned = false;
        if (preferredPrefix != null && !preferredPrefix.isEmpty()) {
            for (int i = 0; i < layer.tailOptions.size(); i++) {
                String option = layer.tailOptions.get(i);
                if (isCompleteOption(option) && option.startsWith(preferredPrefix)) {
                    selectedIndex = i;
                    confirmed = false;
                    partialSelectionPinned = false;
                    return;
                }
            }
        }
        selectedIndex = preferredIndex(null);
        confirmed = false;
        partialSelectionPinned = false;
        leadingSelectedIndex = -1;
    }

    private int preferredIndex(String explicit) {
        if (layer.tailOptions.isEmpty()) {
            return -1;
        }
        String preferred = explicit;
        if (preferred == null || preferred.isEmpty()) {
            List<String> context = new ArrayList<>(lockedSyllables);
            context.addAll(layer.prefix);
            if (!context.isEmpty()) {
                for (int i = 0; i < layer.tailOptions.size(); i++) {
                    String option = layer.tailOptions.get(i);
                    if (!PinyinSyllables.isSyllable(option)
                            || !PinyinSyllables.t9Encode(option).equals(layer.tailDigits)) {
                        continue;
                    }
                    context.add(option);
                    boolean phraseHit = PinyinDictionary.has(T9Segmenter.joinKey(context));
                    context.remove(context.size() - 1);
                    if (phraseHit) {
                        return i;
                    }
                }
            }
            List<String> best = T9Segmenter.bestSegmentation(tailDigits.toString());
            if (!best.isEmpty()) {
                preferred = best.get(best.size() - 1);
            }
        }
        int index = layer.tailOptions.indexOf(preferred);
        return index >= 0 ? index : 0;
    }

    private String selectedOption() {
        if (selectedIndex < 0 || selectedIndex >= layer.tailOptions.size()) {
            return "";
        }
        return layer.tailOptions.get(selectedIndex);
    }

    /**
     * Return the prefix with the leading syllable replaced by the user's
     * selection (if any).  When the user navigates to "zhe" while the
     * segmenter defaulted to "xie", this returns ["zhe", ...] instead
     * of the raw ["xie", ...].
     */
    private List<String> resolvedPrefix() {
        if (layer.prefix.isEmpty() || leadingSelectedIndex < 0) {
            return layer.prefix;
        }
        List<String> opts = PinyinLayer.optionsForDigits(
                PinyinSyllables.t9Encode(layer.prefix.get(0)));
        if (leadingSelectedIndex >= opts.size()) {
            return layer.prefix;
        }
        String chosen = opts.get(leadingSelectedIndex);
        if (!PinyinSyllables.isSyllable(chosen)) {
            return layer.prefix;
        }
        if (!PinyinSyllables.t9Encode(chosen)
                .equals(PinyinSyllables.t9Encode(layer.prefix.get(0)))) {
            return layer.prefix;
        }
        List<String> result = new ArrayList<>(layer.prefix);
        result.set(0, chosen);
        return result;
    }

    private boolean isCompleteOption(String option) {
        return option != null
                && PinyinSyllables.isSyllable(option)
                && PinyinSyllables.t9Encode(option).equals(layer.tailDigits);
    }

    // ----- Loop sound-selection mode -----

    public boolean isLoopMode() {
        return loopMode;
    }

    /**
     * Toggle loop sound-selection mode. When enabled the session overlays a
     * fixed-position reading selector on the current digit buffer; the legacy
     * lockedSyllables/tail state is left untouched and reused when loop mode
     * is switched back off.
     */
    public void setLoopMode(boolean loop) {
        this.loopMode = loop;
        rebuildLoopState();
    }

    /** 0-based index of the syllable position currently being edited. */
    public int getLoopEditPosition() {
        return loopDigitGroups.isEmpty() ? 0 : Math.min(loopEditPosition, loopDigitGroups.size() - 1);
    }

    /** Total number of editable syllable positions in the current segmentation. */
    public int getLoopPositionCount() {
        return loopDigitGroups.size();
    }

    private List<String> loopOptionsForCurrent() {
        if (loopEditPosition < 0 || loopEditPosition >= loopDigitGroups.size()) {
            return Collections.emptyList();
        }
        return PinyinSyllables.syllablesForT9(loopDigitGroups.get(loopEditPosition));
    }

    /**
     * Re-segment the full digit buffer into fixed positions and reset each
     * position's reading to the segmenter's default. Called on every digit /
     * backspace and on mode toggle, so the positions always reflect what the
     * user has typed so far.
     */
    private void rebuildLoopState() {
        String all = getDigits();
        T9Segmenter.Segment seg = T9Segmenter.bestEffort(all);
        List<String> positions = splitKey(seg.phraseKey);
        List<String> newGroups = new ArrayList<>();
        List<String> newReadings = new ArrayList<>();
        for (String syl : positions) {
            newGroups.add(PinyinSyllables.t9Encode(syl));
            newReadings.add(syl);
        }
        // A trailing incomplete remainder becomes its own position so the user
        // can still see / pick a reading for it once it forms a syllable.
        if (!seg.remainder.isEmpty() && positions.isEmpty()) {
            newGroups.add(seg.remainder);
            newReadings.add(seg.remainder);
        }
        loopDigitGroups = newGroups;
        loopReadings = newReadings;
        if (loopEditPosition >= loopDigitGroups.size()) {
            loopEditPosition = 0;
        }
    }

    private static List<String> splitKey(String phraseKey) {
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
