package com.garaho.ime.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.garaho.ime.R;

import java.util.Arrays;

/**
 * Candidate strip (design doc §4.1).
 *
 * <p>Single horizontal row of up to 5 candidates; D-Pad Left/Right shifts focus,
 * D-Pad Down expands to a multi-row grid, OK / {@code 1} commits the highlighted
 * candidate. All focus is driven by physical keys (0-Touch).
 */
public class CandidateBar extends LinearLayout {

    private static final int INLINE_VISIBLE = 5;

    private TextView composingPreview;
    private ViewGroup candidateRow;
    private ViewGroup modeBar;
    private String[] candidates = new String[0];
    private int focusIndex = 0;
    private boolean gridExpanded = false;
    private String modeLabel = "中";
    private CharSequence composingText = "";

    public CandidateBar(Context context) {
        super(context);
        init();
    }

    public CandidateBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        LayoutInflater.from(getContext()).inflate(R.layout.view_candidate_bar_children, this, true);
        composingPreview = findViewById(R.id.composing_preview);
        candidateRow = findViewById(R.id.candidate_row);
        modeBar = findViewById(R.id.mode_bar);
        renderHeader();
    }

    /**
     * iWnn-style quick-switch mode bar: shows one button per entry, highlights
     * the active one. Used while composing is empty (design doc §1 / SettingsPage
     * quick-select). Set {@code labels} empty to hide.
     */
    public void setModeBar(String[] labels, int highlightIndex) {
        modeBar.removeAllViews();
        if (labels == null || labels.length == 0) {
            return;
        }
        int hi = (highlightIndex < 0 || highlightIndex >= labels.length) ? 0 : highlightIndex;
        for (int i = 0; i < labels.length; i++) {
            TextView tv = new TextView(getContext());
            tv.setText(labels[i]);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(28, 14, 28, 14);
            tv.setTextSize(18);
            if (i == hi) {
                tv.setBackgroundColor(Color.rgb(0x33, 0x66, 0x99));
                tv.setTextColor(Color.WHITE);
                tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            } else {
                tv.setBackgroundColor(Color.TRANSPARENT);
                tv.setTextColor(Color.BLACK);
            }
            LayoutParams lp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            modeBar.addView(tv, lp);
        }
        invalidate();
    }

    public void showModeBar(boolean show) {
        modeBar.setVisibility(show ? View.VISIBLE : View.GONE);
        composingPreview.setVisibility(show ? View.GONE : View.VISIBLE);
        candidateRow.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    public void setModeLabel(String label) {
        this.modeLabel = label == null ? "" : label;
        renderHeader();
    }

    public void setComposingText(CharSequence text) {
        this.composingText = text == null ? "" : text;
        renderHeader();
    }

    private void renderHeader() {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        if (modeLabel.length() > 0) {
            sb.append('[').append(modeLabel).append(']').append(' ');
        }
        sb.append(composingText);
        composingPreview.setText(sb);
    }

    public void setCandidates(String[] candidates) {
        this.candidates = candidates == null ? new String[0] : Arrays.copyOf(candidates, candidates.length);
        if (focusIndex >= this.candidates.length) {
            focusIndex = Math.max(0, this.candidates.length - 1);
        }
        render();
    }

    public boolean moveFocus(int delta) {
        if (candidates.length == 0) {
            return false;
        }
        int next = focusIndex + delta;
        if (next < 0 || next >= candidates.length) {
            return false;
        }
        focusIndex = next;
        render();
        return true;
    }

    public boolean expandGrid(boolean expand) {
        if (gridExpanded == expand) {
            return false;
        }
        gridExpanded = expand;
        render();
        return true;
    }

    public int getFocusIndex() {
        return focusIndex;
    }

    public String consumeSelected() {
        if (candidates.length == 0 || focusIndex < 0 || focusIndex >= candidates.length) {
            return null;
        }
        String word = candidates[focusIndex];
        candidates = new String[0];
        focusIndex = 0;
        gridExpanded = false;
        render();
        return word;
    }

    private void render() {
        candidateRow.removeAllViews();
        int columns = gridExpanded ? INLINE_VISIBLE * INLINE_VISIBLE : INLINE_VISIBLE;
        int show = Math.min(columns, candidates.length);
        for (int i = 0; i < show; i++) {
            TextView tv = new TextView(getContext());
            String label = (i + 1) + "." + candidates[i];
            tv.setText(label);
            tv.setPadding(24, 12, 24, 12);
            tv.setTextSize(16);
            if (i == focusIndex) {
                tv.setBackgroundColor(Color.rgb(0x33, 0x66, 0x99));
                tv.setTextColor(Color.WHITE);
                tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            } else {
                tv.setBackgroundColor(Color.TRANSPARENT);
                tv.setTextColor(Color.BLACK);
            }
            LayoutParams lp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            candidateRow.addView(tv, lp);
        }
        invalidate();
    }
}
