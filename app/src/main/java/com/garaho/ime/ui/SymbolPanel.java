package com.garaho.ime.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.garaho.ime.R;
import com.garaho.ime.keymap.InputAction;
import com.garaho.ime.user.PhraseStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Symbol/phrase picker (design doc §4.2 + §2.4).
 *
 * <p>Implemented as a child overlay of the IME input view (a separate
 * WindowManager window does not reliably receive key events inside an IME).
 * The host {@code GarahoImeService} routes D-Pad / OK / BACK to
 * {@link #handleAction(InputAction)} while {@link #isShowing()} is true, making the panel
 * effectively modal. 符号 and 定型文 share the panel; UP past the first row
 * cycles the tab.
 */
public class SymbolPanel {

    public interface OnSymbolPicked {
        void onSymbolPicked(String symbol);
    }

    private static final String[][] SYMBOL_PAGES = {
            {",", ".", "?", "!", ";", ":", "\"", "'", "(", ")", "-", "~", "/", "\\"},
            {"+", "-", "=", "*", "_", "#", "@", "&", "%", "$", "^", "`", "[", "]"},
            {"\u3001", "\u3002", "\uff0c", "\uff1f", "\uff01", "\uff1b", "\uff1a", "\u201c",
             "\u201d", "\u3010", "\u3011", "\uff08", "\uff09", "\u2014"},
    };

    private static final int TAB_SYMBOL = 0;
    private static final int TAB_PHRASE = 1;

    private final Context context;
    private final OnSymbolPicked callback;
    private ViewGroup parent;
    private View root;
    private GridView grid;
    private TextView pageTitle;
    private int tab = TAB_SYMBOL;
    private int page = 0;
    private int focus = 0;
    private boolean showing;
    private boolean attached;
    private List<String> phraseItems = new ArrayList<>();

    public SymbolPanel(Context context, OnSymbolPicked callback) {
        this.context = context;
        this.callback = callback;
    }

    public boolean isShowing() {
        return showing;
    }

    public void show(ViewGroup parent) {
        if (showing) {
            return;
        }
        this.parent = parent;
        if (!attached) {
            root = LayoutInflater.from(context).inflate(R.layout.view_symbol_panel, parent, false);
            pageTitle = root.findViewById(R.id.symbol_page_label);
            grid = root.findViewById(R.id.symbol_grid);
            grid.setNumColumns(4);
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            // Pin to the bottom so the panel sits over the candidate strip in
            // fullscreen mode (rootContainer is full-screen there).
            lp.gravity = Gravity.BOTTOM;
            parent.addView(root, lp);
            attached = true;
        }
        root.setVisibility(View.VISIBLE);
        root.bringToFront();
        reloadPhrases();
        renderTab();
        parent.requestLayout();
        showing = true;
    }

    public void dismiss() {
        if (!showing) {
            return;
        }
        showing = false;
        if (root != null) {
            root.setVisibility(View.GONE);
        }
        if (parent != null) {
            parent.requestLayout();
        }
    }

    /** @return true if the action was consumed (the service treats the panel as modal). */
    public boolean handleAction(InputAction action) {
        switch (action) {
            case NAV_LEFT:
                moveFocus(-1);
                return true;
            case NAV_RIGHT:
                moveFocus(1);
                return true;
            case NAV_UP:
                if (focus < 4) {
                    cycleTab();
                } else {
                    moveFocus(-4);
                }
                return true;
            case NAV_DOWN:
                moveFocus(4);
                return true;
            case CONFIRM_SELECTION: {
                String[] items = currentItems();
                if (focus >= 0 && focus < items.length && callback != null) {
                    callback.onSymbolPicked(items[focus]);
                }
                dismiss();
                return true;
            }
            default:
                return true; // modal: swallow other keys while open
        }
    }

    private void reloadPhrases() {
        phraseItems.clear();
        for (PhraseStore.Entry e : PhraseStore.get(context).entries()) {
            if (!e.text.isEmpty()) {
                phraseItems.add(e.text);
            }
        }
    }

    private String[] currentItems() {
        if (tab == TAB_PHRASE) {
            return phraseItems.toArray(new String[0]);
        }
        return SYMBOL_PAGES[page];
    }

    private void cycleTab() {
        tab = (tab == TAB_SYMBOL) ? TAB_PHRASE : TAB_SYMBOL;
        focus = 0;
        page = 0;
        if (tab == TAB_PHRASE) {
            reloadPhrases();
        }
        renderTab();
    }

    private void moveFocus(int delta) {
        String[] items = currentItems();
        if (items.length == 0) {
            return;
        }
        int next = focus + delta;
        if (next < 0) {
            next = 0;
        }
        if (next >= items.length) {
            next = items.length - 1;
        }
        if (next == focus) {
            return;
        }
        focus = next;
        renderTab();
    }

    private void renderTab() {
        pageTitle.setText(tab == TAB_SYMBOL
                ? context.getString(R.string.symbol_tab_symbol)
                : context.getString(R.string.symbol_tab_phrase));
        final String[] items = currentItems();
        final int focusRef = focus;
        grid.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return items.length;
            }

            @Override
            public Object getItem(int position) {
                return items[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parentView) {
                TextView tv;
                if (convertView instanceof TextView) {
                    tv = (TextView) convertView;
                } else {
                    tv = new TextView(context);
                    tv.setGravity(Gravity.CENTER);
                    tv.setPadding(18, 18, 18, 18);
                    tv.setLayoutParams(new GridView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                tv.setText(items[position]);
                tv.setTextSize(14);
                if (position == focusRef) {
                    tv.setBackgroundResource(R.drawable.list_focus_bg);
                    tv.setTextColor(Color.BLACK);
                } else {
                    tv.setBackgroundColor(Color.TRANSPARENT);
                    tv.setTextColor(Color.BLACK);
                }
                return tv;
            }
        });
    }
}
