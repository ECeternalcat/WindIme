package com.garaho.ime.engine;

import java.util.List;

/**
 * Push-based callback for engine state changes. The {@code GarahoImeService}
 * implements this to refresh the composing preview and candidate strip.
 */
public interface EngineListener {

    /**
     * @param composing human-readable composing text (e.g. {@code "ni'hao"});
     *                  empty string means the buffer is cleared.
     */
    void onComposingChanged(String composing);

    /** @param candidates ordered candidate list; empty when nothing matches. */
    void onCandidatesChanged(List<String> candidates);

    /** @param text the candidate text that was committed to the editor. */
    void onCommit(String text);
}
