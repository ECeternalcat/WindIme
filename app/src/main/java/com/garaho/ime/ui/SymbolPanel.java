package com.garaho.ime.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.BaseAdapter;

import com.garaho.ime.R;
import com.garaho.ime.user.PhraseStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen symbol matrix (design doc §4.2) with a 符号 / 定型文 tab header
 * (design doc §2.4 quick-insert). Pops up as an overlay that intercepts all
 * D-Pad events: 4-way movement shifts focus across the 2D grid; pressing UP
 * past the first row cycles the tab; OK commits the highlighted item and
 * dismisses. 0-Touch only.
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
    private WindowManager windowManager;
    private View root;
    private GridView grid;
    private TextView pageTitle;
    private int tab = TAB_SYMBOL;
    private int page = 0;
    private int focus = 0;
    private boolean showing;
    private List<String> phraseItems = new ArrayList<>();

    public SymbolPanel(Context context, OnSymbolPicked callback) {
        this.context = context;
        this.callback = callback;
    }

    public void show() {
        if (showing) {
            return;
        }
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        root = LayoutInflater.from(context).inflate(R.layout.view_symbol_panel, null);
        pageTitle = root.findViewById(R.id.symbol_page_label);
        grid = root.findViewById(R.id.symbol_grid);
        grid.setNumColumns(4);
        reloadPhrases();
        renderTab();
        root.setFocusable(true);
        root.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                return SymbolPanel.this.onKey(keyCode);
            }
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        lp.token = null;
        try {
            windowManager.addView(root, lp);
            root.requestFocus();
            showing = true;
        } catch (RuntimeException ignored) {
        }
    }

    public void dismiss() {
        if (!showing || windowManager == null) {
            return;
        }
        try {
            windowManager.removeView(root);
        } catch (RuntimeException ignored) {
        }
        showing = false;
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

    private boolean onKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                moveFocus(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                moveFocus(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (focus < 4) {
                    cycleTab();
                } else {
                    moveFocus(-4);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                moveFocus(4);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: {
                String[] items = currentItems();
                if (focus >= 0 && focus < items.length && callback != null) {
                    callback.onSymbolPicked(items[focus]);
                }
                dismiss();
                return true;
            }
            case KeyEvent.KEYCODE_BACK:
                dismiss();
                return true;
            default:
                return false;
        }
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
        grid.setSelection(focus);
        ((BaseAdapter) grid.getAdapter()).notifyDataSetChanged();
    }

    private void renderTab() {
        pageTitle.setText(tab == TAB_SYMBOL
                ? context.getString(R.string.symbol_tab_symbol)
                : context.getString(R.string.symbol_tab_phrase));
        final String[] items = currentItems();
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
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv;
                if (convertView instanceof TextView) {
                    tv = (TextView) convertView;
                } else {
                    tv = new TextView(context);
                    tv.setGravity(Gravity.CENTER);
                    tv.setPadding(24, 28, 24, 28);
                    tv.setLayoutParams(new GridView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                tv.setText(items[position]);
                if (position == focus) {
                    tv.setBackgroundColor(Color.rgb(0x33, 0x66, 0x99));
                    tv.setTextColor(Color.WHITE);
                } else {
                    tv.setBackgroundColor(Color.TRANSPARENT);
                    tv.setTextColor(Color.BLACK);
                }
                return tv;
            }
        });
        grid.setSelection(focus);
    }
}
