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
 * <p>Chinese T9 uses three rows: composing preview, pinyin readings and word
 * candidates. Up/Down switches between the latter two rows; Left/Right moves
 * within the active row. Other modes hide the pinyin row.
 */
public class CandidateBar extends LinearLayout {

    private static final int INLINE_VISIBLE = 5;
    private static final int PINYIN_VISIBLE = 6;

    public enum InputLayer {
        PINYIN,
        CANDIDATE
    }

    private TextView composingPreview;
    private TextView backendStatus;
    private ViewGroup pinyinRow;
    private ViewGroup candidateRow;
    private ViewGroup modeBar;
    private String[] candidates = new String[0];
    private String[] pinyinOptions = new String[0];
    private int candidateFocusIndex = 0;
    private int pinyinFocusIndex = 0;
    private InputLayer activeLayer = InputLayer.CANDIDATE;
    private boolean gridExpanded;
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
        backendStatus = findViewById(R.id.backend_status);
        pinyinRow = findViewById(R.id.pinyin_row);
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
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setPadding(6, 8, 6, 8);
            tv.setTextSize(13);
            tv.setMinimumHeight(0);
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
        pinyinRow.setVisibility(!show && pinyinOptions.length > 0 ? View.VISIBLE : View.GONE);
        candidateRow.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    public void setModeLabel(String label) {
        this.modeLabel = label == null ? "" : label;
        renderHeader();
    }

    public void setBackendStatus(String status) {
        backendStatus.setText(status == null ? "" : status);
        backendStatus.setVisibility(status == null || status.isEmpty() ? View.GONE : View.VISIBLE);
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
        if (candidateFocusIndex >= this.candidates.length) {
            candidateFocusIndex = Math.max(0, this.candidates.length - 1);
        }
        render();
    }

    public void setPinyinOptions(String[] options, int selectedIndex) {
        String[] next = options == null ? new String[0] : Arrays.copyOf(options, options.length);
        boolean firstLayeredState = pinyinOptions.length == 0 && next.length > 0;
        pinyinOptions = next;
        if (pinyinOptions.length == 0) {
            pinyinFocusIndex = 0;
            activeLayer = InputLayer.CANDIDATE;
        } else {
            pinyinFocusIndex = selectedIndex >= 0 && selectedIndex < pinyinOptions.length
                    ? selectedIndex
                    : Math.min(pinyinFocusIndex, pinyinOptions.length - 1);
            if (firstLayeredState) {
                activeLayer = InputLayer.PINYIN;
            }
        }
        pinyinRow.setVisibility(pinyinOptions.length > 0 ? View.VISIBLE : View.GONE);
        render();
    }

    public boolean moveFocus(int delta) {
        String[] items = activeLayer == InputLayer.PINYIN ? pinyinOptions : candidates;
        if (items.length == 0) {
            return false;
        }
        int current = activeLayer == InputLayer.PINYIN ? pinyinFocusIndex : candidateFocusIndex;
        int next = current + delta;
        if (next < 0 || next >= items.length) {
            return false;
        }
        if (activeLayer == InputLayer.PINYIN) {
            pinyinFocusIndex = next;
        } else {
            candidateFocusIndex = next;
        }
        render();
        return true;
    }

    public boolean moveLayer(boolean down) {
        if (pinyinOptions.length == 0) {
            return false;
        }
        if (down && candidates.length == 0) {
            return true;
        }
        InputLayer target = down ? InputLayer.CANDIDATE : InputLayer.PINYIN;
        if (activeLayer == target) {
            return true;
        }
        activeLayer = target;
        render();
        return true;
    }

    /** Legacy candidate expansion used by non-layered input modes. */
    public boolean expandGrid(boolean expand) {
        if (gridExpanded == expand) {
            return false;
        }
        gridExpanded = expand;
        render();
        return true;
    }

    public int getFocusIndex() {
        return candidateFocusIndex;
    }

    public int getPinyinFocusIndex() {
        return pinyinFocusIndex;
    }

    public InputLayer getActiveLayer() {
        return activeLayer;
    }

    public boolean activateCandidateLayer() {
        if (candidates.length == 0) {
            return false;
        }
        activeLayer = InputLayer.CANDIDATE;
        render();
        return true;
    }

    public void activatePinyinLayer() {
        if (pinyinOptions.length > 0) {
            activeLayer = InputLayer.PINYIN;
            render();
        }
    }

    public void resetCandidateFocus() {
        if (candidateFocusIndex != 0) {
            candidateFocusIndex = 0;
            render();
        }
    }

    public String consumeSelected() {
        if (candidates.length == 0 || candidateFocusIndex < 0 || candidateFocusIndex >= candidates.length) {
            return null;
        }
        String word = candidates[candidateFocusIndex];
        candidates = new String[0];
        candidateFocusIndex = 0;
        gridExpanded = false;
        render();
        return word;
    }

    private void render() {
        renderPinyinOptions();
        candidateRow.removeAllViews();
        int window = gridExpanded ? INLINE_VISIBLE * INLINE_VISIBLE : INLINE_VISIBLE;
        int start = visibleStart(candidateFocusIndex, candidates.length, window);
        int end = Math.min(start + window, candidates.length);
        for (int i = start; i < end; i++) {
            TextView tv = new TextView(getContext());
            String label = (i + 1) + "." + candidates[i];
            tv.setText(label);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setPadding(6, 8, 6, 8);
            tv.setTextSize(13);
            tv.setMinimumHeight(0);
            if (i == candidateFocusIndex) {
                tv.setBackgroundColor(activeLayer == InputLayer.CANDIDATE
                        ? Color.rgb(0x33, 0x66, 0x99)
                        : Color.rgb(0x99, 0xBB, 0xCC));
                tv.setTextColor(activeLayer == InputLayer.CANDIDATE ? Color.WHITE : Color.BLACK);
                tv.setTypeface(tv.getTypeface(), activeLayer == InputLayer.CANDIDATE
                        ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL);
            } else {
                tv.setBackgroundColor(Color.TRANSPARENT);
                tv.setTextColor(Color.BLACK);
            }
            LayoutParams lp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            candidateRow.addView(tv, lp);
        }
        invalidate();
    }

    private void renderPinyinOptions() {
        pinyinRow.removeAllViews();
        int start = visibleStart(pinyinFocusIndex, pinyinOptions.length, PINYIN_VISIBLE);
        int end = Math.min(start + PINYIN_VISIBLE, pinyinOptions.length);
        for (int i = start; i < end; i++) {
            TextView tv = new TextView(getContext());
            tv.setText(pinyinOptions[i]);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setPadding(6, 8, 6, 8);
            tv.setTextSize(13);
            tv.setMinimumHeight(0);
            if (i == pinyinFocusIndex) {
                tv.setBackgroundColor(activeLayer == InputLayer.PINYIN
                        ? Color.rgb(0x33, 0x66, 0x99)
                        : Color.rgb(0x99, 0xBB, 0xCC));
                tv.setTextColor(activeLayer == InputLayer.PINYIN ? Color.WHITE : Color.BLACK);
                tv.setTypeface(tv.getTypeface(), activeLayer == InputLayer.PINYIN
                        ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL);
            } else {
                tv.setBackgroundColor(Color.TRANSPARENT);
                tv.setTextColor(Color.BLACK);
            }
            pinyinRow.addView(tv, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private static int visibleStart(int focus, int count, int window) {
        if (count <= window || focus < window) {
            return 0;
        }
        return Math.min(focus - window + 1, count - window);
    }
}
