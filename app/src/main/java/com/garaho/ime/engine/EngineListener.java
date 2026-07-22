package com.garaho.ime.engine;

import java.util.List;

/**
 * Push-based callback for engine state changes. The {@code GarahoImeService}
 * implements this to refresh the composing preview and candidate strip.
 */
public interface EngineListener {

    /**
     * @param composing human-readable composing text (e.g. {@code "ni'hao"}).
     *                  May be a {@link android.text.Spanned} carrying inline
     *                  styling (Multi-tap engines highlight the cycling letter).
     *                  Empty string means the buffer is cleared.
     */
    void onComposingChanged(CharSequence composing);

    /** @param candidates ordered candidate list; empty when nothing matches. */
    void onCandidatesChanged(List<String> candidates);

    /** @param text the candidate text that was committed to the editor. */
    void onCommit(String text);
}
