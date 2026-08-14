package com.garaho.ime.engine;

import java.util.List;

/** Chinese T9 engine capabilities used by the three-row composing UI. */
public interface LayeredPinyinEngine {

    List<String> getPinyinOptions();

    int getSelectedPinyinIndex();

    /** Preview an option and refresh word candidates without locking it. */
    boolean previewPinyinOption(int index);

    /** Lock a complete syllable so the next digit starts a new syllable. */
    boolean confirmPinyinOption(int index);

    /**
     * Loop sound-selection mode: lock the reading at the current syllable
     * position and advance to the next (wrapping after the last), without
     * leaving the sound row.
     */
    boolean confirmAndAdvancePinyin(int index);

    /** Enable/disable loop sound-selection mode for the current session. */
    void setLoopMode(boolean loop);

    boolean isLoopMode();

    /** 0-based index of the syllable position being edited (loop mode). */
    int getLoopEditPosition();

    /** Total editable syllable positions in the current segmentation (loop mode). */
    int getLoopPositionCount();
}
