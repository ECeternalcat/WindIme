package com.osfans.trime.core;

/** Commit payload (JNI contract: ctor {@code (String text)}). */
public final class CommitProto {
    public final String text;

    public CommitProto(String text) {
        this.text = text;
    }
}
