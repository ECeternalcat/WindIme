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
}
