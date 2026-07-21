package com.osfans.trime.core;

/** Single candidate (JNI contract: ctor {@code (String text, String comment, String label)}). */
public final class CandidateProto {
    public final String text;
    public final String comment;
    public final String label;

    public CandidateProto(String text, String comment, String label) {
        this.text = text;
        this.comment = comment;
        this.label = label;
    }
}
