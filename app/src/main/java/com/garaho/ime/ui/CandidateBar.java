package com.garaho.ime.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
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
    private TextView positionIndicator;
    private ViewGroup pinyinRow;
    private HorizontalScrollView candidateScroll;
    private ViewGroup candidateRow;
    private ViewGroup modeBar;
    private View focusedCandidateView;
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
        positionIndicator = findViewById(R.id.position_indicator);
        pinyinRow = findViewById(R.id.pinyin_row);
        candidateScroll = findViewById(R.id.candidate_scroll);
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
                tv.setBackgroundResource(R.drawable.list_focus_bg);
                tv.setTextColor(Color.BLACK);
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
        candidateScroll.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    public void setModeLabel(String label) {
        this.modeLabel = label == null ? "" : label;
        renderHeader();
    }

    public void setBackendStatus(String status) {
        String next = status == null ? "" : status;
        if (next.contentEquals(backendStatus.getText())) {
            return;
        }
        backendStatus.setText(next);
        backendStatus.setVisibility(status == null || status.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public void setComposingText(CharSequence text) {
        CharSequence next = text == null ? "" : text;
        if (android.text.TextUtils.equals(this.composingText, next)) {
            return;
        }
        this.composingText = next;
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
        String[] next = candidates == null ? new String[0] : candidates;
        if (Arrays.equals(this.candidates, next)) {
            return;
        }
        this.candidates = Arrays.copyOf(next, next.length);
        // Refresh correction (improvement doc §3): a fresh candidate list
        // always starts at the top, so a stale focus index can never commit a
        // word that is no longer the one the user sees highlighted.
        candidateFocusIndex = 0;
        render();
    }

    public void setPinyinOptions(String[] options, int selectedIndex) {
        String[] next = options == null ? new String[0] : Arrays.copyOf(options, options.length);
        if (Arrays.equals(this.pinyinOptions, next)
                && (next.length == 0 || this.pinyinFocusIndex == selectedIndex)) {
            return;
        }
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
                // When the engine already has word candidates, focus those
                // first. The pinyin row is the reading selector and should be
                // reached with UP; this matches the syllable-by-syllable
                // flip-phone flow (ce -> 测, then shi -> 试).
                activeLayer = this.candidates.length > 0
                        ? InputLayer.CANDIDATE : InputLayer.PINYIN;
            }
        }
        pinyinRow.setVisibility(pinyinOptions.length > 0 ? View.VISIBLE : View.GONE);
        render();
    }

    /** Update both layered rows with one layout/render pass. */
    public void setCandidatesAndPinyinOptions(String[] nextCandidates,
                                               String[] options,
                                               int selectedIndex) {
        String[] next = nextCandidates == null ? new String[0] : nextCandidates;
        String[] nextPinyin = options == null ? new String[0] : options;
        boolean candidatesSame = Arrays.equals(this.candidates, next);
        boolean pinyinSame = Arrays.equals(this.pinyinOptions, nextPinyin)
                && (nextPinyin.length == 0 || this.pinyinFocusIndex == selectedIndex);
        if (candidatesSame && pinyinSame) {
            return;
        }
        this.candidates = Arrays.copyOf(next, next.length);
        if (!candidatesSame) {
            candidateFocusIndex = 0;
        }
        boolean firstLayeredState = pinyinOptions.length == 0 && nextPinyin.length > 0;
        this.pinyinOptions = Arrays.copyOf(nextPinyin, nextPinyin.length);
        if (nextPinyin.length == 0) {
            pinyinFocusIndex = 0;
            activeLayer = InputLayer.CANDIDATE;
        } else {
            pinyinFocusIndex = selectedIndex >= 0 && selectedIndex < nextPinyin.length
                    ? selectedIndex
                    : Math.min(pinyinFocusIndex, nextPinyin.length - 1);
            if (firstLayeredState) {
                activeLayer = this.candidates.length > 0
                        ? InputLayer.CANDIDATE : InputLayer.PINYIN;
            }
        }
        pinyinRow.setVisibility(nextPinyin.length > 0 ? View.VISIBLE : View.GONE);
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

    /** Select the useful default layer after a digit was typed. */
    public void activateDefaultLayer() {
        if (candidates.length > 0) {
            activeLayer = InputLayer.CANDIDATE;
        } else if (pinyinOptions.length > 0) {
            activeLayer = InputLayer.PINYIN;
        } else {
            activeLayer = InputLayer.CANDIDATE;
        }
        render();
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
        focusedCandidateView = null;
        int maxCellPx = maxCellWidthPx();
        // Show every candidate at its natural width. Cells are wrapped in a
        // HorizontalScrollView, so long lists scroll instead of being squeezed
        // and ellipsized; each cell is capped to the screen width so a single
        // over-long phrase ellipsizes at the screen edge rather than running
        // off (improvement doc §3 / user report on "我爱你哦", "好家伙").
        for (int i = 0; i < candidates.length; i++) {
            TextView tv = new TextView(getContext());
            String label = (i + 1) + "." + candidates[i];
            tv.setText(label);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setPadding(6, 8, 6, 8);
            tv.setTextSize(13);
            tv.setMinimumHeight(0);
            tv.setMaxWidth(maxCellPx);
            if (i == candidateFocusIndex) {
                if (activeLayer == InputLayer.CANDIDATE) {
                    // System-settings style: light-blue fill + thin border, dark text.
                    tv.setBackgroundResource(R.drawable.list_focus_bg);
                    tv.setTextColor(Color.BLACK);
                    tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
                } else {
                    // Dim highlight on the non-active layer.
                    tv.setBackgroundColor(Color.rgb(0xE3, 0xE3, 0xE3));
                    tv.setTextColor(Color.BLACK);
                    tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.NORMAL);
                }
                focusedCandidateView = tv;
            } else {
                tv.setBackgroundColor(Color.TRANSPARENT);
                tv.setTextColor(Color.BLACK);
            }
            LayoutParams lp = new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            candidateRow.addView(tv, lp);
        }
        updatePositionIndicator();
        scrollToFocused();
        invalidate();
    }

    /** Bring the focused candidate into view after the row is laid out. */
    private void scrollToFocused() {
        final View focus = focusedCandidateView;
        if (focus == null || candidateScroll == null) {
            return;
        }
        candidateScroll.post(new Runnable() {
            @Override
            public void run() {
                int target = Math.max(0, focus.getLeft() - 2);
                // D-pad input should move immediately; smooth scrolling queues
                // animations on every key press and is noticeably laggy on 1GB
                // flip-phone hardware.
                candidateScroll.scrollTo(target, 0);
            }
        });
    }

    private int maxCellWidthPx() {
        int sw = getContext().getResources().getDisplayMetrics().widthPixels;
        return Math.max(40, sw - 8);
    }

    /**
     * Show a compact position label. Pinyin uses a fixed-window threshold; the
     * candidate row now scrolls horizontally and shows its "{@code n/total}"
     * position only when it actually overflows (checked after layout).
     */
    private void updatePositionIndicator() {
        if (positionIndicator == null) {
            return;
        }
        final String pinyinLabel;
        if (activeLayer == InputLayer.PINYIN && pinyinOptions.length > PINYIN_VISIBLE) {
            pinyinLabel = CandidatePagination.positionLabel(
                    pinyinFocusIndex, pinyinOptions.length, PINYIN_VISIBLE);
        } else {
            pinyinLabel = "";
        }
        positionIndicator.post(new Runnable() {
            @Override
            public void run() {
                String label = pinyinLabel;
                if (label.isEmpty() && activeLayer == InputLayer.CANDIDATE
                        && candidateScroll != null && candidates.length > 1
                        && (candidateScroll.canScrollHorizontally(-1)
                                || candidateScroll.canScrollHorizontally(1))) {
                    label = (candidateFocusIndex + 1) + "/" + candidates.length;
                }
                if (label.isEmpty()) {
                    positionIndicator.setText("");
                    positionIndicator.setVisibility(View.GONE);
                } else {
                    positionIndicator.setText(label);
                    positionIndicator.setVisibility(View.VISIBLE);
                }
            }
        });
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
                if (activeLayer == InputLayer.PINYIN) {
                    tv.setBackgroundResource(R.drawable.list_focus_bg);
                    tv.setTextColor(Color.BLACK);
                    tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
                } else {
                    tv.setBackgroundColor(Color.rgb(0xE3, 0xE3, 0xE3));
                    tv.setTextColor(Color.BLACK);
                    tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.NORMAL);
                }
            } else {
                tv.setBackgroundColor(Color.TRANSPARENT);
                tv.setTextColor(Color.BLACK);
            }
            pinyinRow.addView(tv, new LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, Math.max(1f, (float) pinyinOptions[i].length())));
        }
    }

    private static int visibleStart(int focus, int count, int window) {
        return CandidatePagination.visibleStart(focus, count, window);
    }
}
