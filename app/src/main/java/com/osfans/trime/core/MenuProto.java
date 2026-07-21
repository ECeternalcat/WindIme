package com.osfans.trime.core;

/**
 * Candidate menu page (JNI contract:
 * ctor {@code (int pageSize, int pageNumber, boolean isLastPage,
 * int highlightedCandidateIndex, CandidateProto[] candidates,
 * String selectKeys, String[] selectLabels)}).
 */
public final class MenuProto {
    public final int pageSize;
    public final int pageNumber;
    public final boolean isLastPage;
    public final int highlightedCandidateIndex;
    public final CandidateProto[] candidates;
    public final String selectKeys;
    public final String[] selectLabels;

    public MenuProto(int pageSize, int pageNumber, boolean isLastPage,
                     int highlightedCandidateIndex, CandidateProto[] candidates,
                     String selectKeys, String[] selectLabels) {
        this.pageSize = pageSize;
        this.pageNumber = pageNumber;
        this.isLastPage = isLastPage;
        this.highlightedCandidateIndex = highlightedCandidateIndex;
        this.candidates = candidates;
        this.selectKeys = selectKeys;
        this.selectLabels = selectLabels;
    }
}
