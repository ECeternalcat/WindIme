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
            return true;
        }
        if (!lockedSyllables.isEmpty()) {
            String restored = lockedSyllables.remove(lockedSyllables.size() - 1);
            tailDigits.append(PinyinSyllables.t9Encode(restored));
            recompute(restored);
            return true;
        }
        return false;
    }

    public boolean preview(int index) {
        if (index < 0 || index >= layer.tailOptions.size()) {
            return false;
        }
        selectedIndex = index;
        confirmed = false;
        boolean complete = isCompleteOption(selectedOption());
        partialSelectionPinned = !complete;
        completeSelectionPinned = complete;
        return true;
    }

    public boolean confirm(int index) {
        if (!preview(index)) {
            return false;
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
    }

    public List<String> getOptions() {
        return layer.tailOptions;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public String getPhraseKey() {
        List<String> parts = new ArrayList<>(lockedSyllables);
        parts.addAll(layer.prefix);
        String option = selectedOption();
        if (isCompleteOption(option)) {
            parts.add(option);
        }
        return T9Segmenter.joinKey(parts);
    }

    public String getComposing() {
        String phrase = getPhraseKey();
        String option = selectedOption();
        if (!isCompleteOption(option)) {
            List<String> parts = new ArrayList<>(lockedSyllables);
            parts.addAll(layer.prefix);
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
        lockedSyllables.addAll(layer.prefix);
        String option = selectedOption();
        if (!option.isEmpty()) {
            lockedSyllables.add(option);
        }
        tailDigits.setLength(0);
        layer = PinyinLayer.segmentForLayer("");
        selectedIndex = -1;
        confirmed = false;
        partialSelectionPinned = false;
        completeSelectionPinned = false;
    }

    private void recompute(String preferredOption) {
        layer = PinyinLayer.segmentForLayer(tailDigits.toString());
        selectedIndex = preferredIndex(preferredOption);
        confirmed = false;
        partialSelectionPinned = false;
        completeSelectionPinned = false;
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

    private boolean isCompleteOption(String option) {
        return option != null
                && PinyinSyllables.isSyllable(option)
                && PinyinSyllables.t9Encode(option).equals(layer.tailDigits);
    }
}
