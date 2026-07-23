package com.garaho.ime.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CandidatePaginationTest {

    @Test
    public void pageCountOneWhenFitsOrEmpty() {
        assertEquals(1, CandidatePagination.pageCount(0, 5));
        assertEquals(1, CandidatePagination.pageCount(5, 5));
        assertEquals(1, CandidatePagination.pageCount(3, 5));
        assertEquals(1, CandidatePagination.pageCount(7, 0));
    }

    @Test
    public void pageCountRoundsUp() {
        assertEquals(2, CandidatePagination.pageCount(6, 5));
        assertEquals(3, CandidatePagination.pageCount(12, 5));
        assertEquals(3, CandidatePagination.pageCount(15, 5));
        assertEquals(4, CandidatePagination.pageCount(16, 5));
    }

    @Test
    public void visibleStartPageAligned() {
        // window 5, total 12 -> pages [0..4],[5..9],[10..11]
        assertEquals(0, CandidatePagination.visibleStart(0, 12, 5));
        assertEquals(0, CandidatePagination.visibleStart(4, 12, 5));
        assertEquals(5, CandidatePagination.visibleStart(5, 12, 5));
        assertEquals(5, CandidatePagination.visibleStart(9, 12, 5));
        assertEquals(10, CandidatePagination.visibleStart(10, 12, 5));
        assertEquals(10, CandidatePagination.visibleStart(11, 12, 5));
    }

    @Test
    public void visibleStartWhenFitsIsZero() {
        assertEquals(0, CandidatePagination.visibleStart(0, 5, 5));
        assertEquals(0, CandidatePagination.visibleStart(4, 5, 5));
    }

    @Test
    public void currentPageClampsToLastPage() {
        assertEquals(2, CandidatePagination.currentPage(11, 12, 5));
        assertEquals(2, CandidatePagination.currentPage(99, 12, 5));
        assertEquals(0, CandidatePagination.currentPage(0, 12, 5));
    }

    @Test
    public void positionLabelHiddenWhenSinglePage() {
        assertEquals("", CandidatePagination.positionLabel(0, 5, 5));
        assertEquals("", CandidatePagination.positionLabel(4, 5, 5));
        assertEquals("", CandidatePagination.positionLabel(0, 0, 5));
    }

    @Test
    public void positionLabelShowsOneBasedPosition() {
        assertEquals("1/12", CandidatePagination.positionLabel(0, 12, 5));
        assertEquals("6/12", CandidatePagination.positionLabel(5, 12, 5));
        assertEquals("12/12", CandidatePagination.positionLabel(11, 12, 5));
        assertEquals("1/6", CandidatePagination.positionLabel(0, 6, 5));
    }

    @Test
    public void positionLabelClampsOutOfRangeFocus() {
        assertEquals("1/12", CandidatePagination.positionLabel(-3, 12, 5));
        assertEquals("12/12", CandidatePagination.positionLabel(99, 12, 5));
    }
}
