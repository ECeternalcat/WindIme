package com.garaho.ime.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.garaho.ime.R;
import com.garaho.ime.keymap.InputAction;
import com.garaho.ime.user.PhraseStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Symbol/phrase picker with a top tab bar (中 / EN / 颜 / α / 定型文).
 *
 * <p>Navigation model (two focus layers):
 * <ul>
 *   <li><b>TAB layer</b> – LEFT/RIGHT switches tab, DOWN enters content, OK enters content.</li>
 *   <li><b>CONTENT layer</b> – LEFT/RIGHT/UP/DOWN moves within the grid or list;
 *       UP past the first row returns to the TAB layer; OK picks the item.</li>
 * </ul>
 *
 * <p>Kaomoji and phrases use a single-column {@link ListView} (variable-width items);
 * symbol tabs use a 4-column {@link GridView}.
 */
public class SymbolPanel {

    public interface OnSymbolPicked {
        void onSymbolPicked(String symbol);
    }

    // --- Tab definitions ---
    private static final int TAB_CN = 0;
    private static final int TAB_EN = 1;
    private static final int TAB_KAO = 2;
    private static final int TAB_GREEK = 3;
    private static final int TAB_PHRASE = 4;
    private static final int TAB_COUNT = 5;

    private static final String[] TAB_LABELS = {"中", "EN", "颜", "α", "定型文"};

    private static final int GRID_COLUMNS = 4;

    // --- Symbol data ---
    private static final String[] CN_SYMBOLS = {
            "，", "。", "、", "！", "？", "；", "：", "\u201c",
            "\u201d", "\u2018", "\u2019", "（", "）", "【", "】", "《",
            "》", "〈", "〉", "〔", "〕", "—", "…", "·",
            "／", "＼", "￥", "％", "＋", "－", "＝", "＊", "＃", "＠", "＆"
    };

    private static final String[] EN_SYMBOLS = {
            ",", ".", "!", "?", ";", ":", "\"", "'",
            "(", ")", "[", "]", "{", "}", "<", ">",
            "-", "_", "/", "\\", "%", "\u2030", "+", "-",
            "\u00d7", "\u00f7", "=", "\u2260", "*", "#", "@", "&",
            "^", "~", "`", "|", "$", "\u20ac", "\u00a3", "\u00a5",
            "\u2248", "\u00b1", "\u2264", "\u2265", "\u221e"
    };

    private static final String[] GREEK_SYMBOLS = {
            "Α", "α", "Β", "β", "Γ", "γ", "Δ", "δ",
            "Ε", "ε", "Ζ", "ζ", "Η", "η", "Θ", "θ",
            "Ι", "ι", "Κ", "κ", "Λ", "λ", "Μ", "μ",
            "Ν", "ν", "Ξ", "ξ", "Ο", "ο", "Π", "π",
            "Ρ", "ρ", "Σ", "σ", "Τ", "τ", "Υ", "υ",
            "Φ", "φ", "Χ", "χ", "Ψ", "ψ", "Ω", "ω"
    };

    private static final String[] KAOMOJI = {
            "(´･ω･`)", "(`･ω･´)", "(｀-´)>", "(´；ω；`)",
            "（　ﾟДﾟ）", "┐('～`；)┌", "（´∀｀）", "Σ(゜д゜;)",
            "（・Ａ・）", "(￣ー￣)", "ヽ(ｏ`皿′ｏ)ﾉ", "m(_ _)m",
            "(≧ロ≦)", "(ΘεΘ;)", "₍^. .^₎⟆", "(>_<) (>_<)>",
            "(-_-)zzz", "(*￣m￣)", "ヽ（´ー｀）┌", "(╯°□°）╯︵ ┻━┻"
    };

    // --- Focus layers ---
    private static final int LAYER_TAB = 0;
    private static final int LAYER_CONTENT = 1;

    private final Context context;
    private final OnSymbolPicked callback;

    private ViewGroup parent;
    private View root;
    private LinearLayout tabBar;
    private GridView grid;
    private ListView list;
    private TextView emptyHint;

    private boolean showing;
    private boolean attached;

    private int layer = LAYER_TAB;
    private int tabIndex = TAB_CN;
    private int contentFocus = 0;
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
            tabBar = root.findViewById(R.id.symbol_tab_bar);
            grid = root.findViewById(R.id.symbol_grid);
            list = root.findViewById(R.id.symbol_list);
            emptyHint = root.findViewById(R.id.symbol_empty_hint);
            grid.setNumColumns(GRID_COLUMNS);
            grid.setFocusable(false);
            grid.setFocusableInTouchMode(false);
            grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            list.setFocusable(false);
            list.setFocusableInTouchMode(false);
            list.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.BOTTOM;
            parent.addView(root, lp);
            attached = true;
        }
        root.setVisibility(View.VISIBLE);
        root.bringToFront();
        layer = LAYER_TAB;
        tabIndex = TAB_CN;
        contentFocus = 0;
        reloadPhrases();
        render();
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

    /** @return true if the action was consumed (the panel is modal). */
    public boolean handleAction(InputAction action) {
        switch (action) {
            case NAV_LEFT:
                if (layer == LAYER_TAB) {
                    switchTab(-1);
                } else {
                    moveContentFocus(-1);
                }
                return true;
            case NAV_RIGHT:
                if (layer == LAYER_TAB) {
                    switchTab(1);
                } else {
                    moveContentFocus(1);
                }
                return true;
            case NAV_UP:
                if (layer == LAYER_TAB) {
                    switchTab(-1);
                } else {
                    if (isListMode()) {
                        if (contentFocus <= 0) {
                            layer = LAYER_TAB;
                            render();
                        } else {
                            moveContentFocus(-1);
                        }
                    } else {
                        if (contentFocus < GRID_COLUMNS) {
                            layer = LAYER_TAB;
                            render();
                        } else {
                            moveContentFocus(-GRID_COLUMNS);
                        }
                    }
                }
                return true;
            case NAV_DOWN:
                if (layer == LAYER_TAB) {
                    enterContent();
                } else {
                    moveContentFocus(isListMode() ? 1 : GRID_COLUMNS);
                }
                return true;
            case CONFIRM_SELECTION:
                if (layer == LAYER_TAB) {
                    enterContent();
                } else {
                    pickCurrent();
                }
                return true;
            default:
                return true; // modal: swallow
        }
    }

    // --- Navigation helpers ---

    private void switchTab(int delta) {
        int next = tabIndex + delta;
        if (next < 0) next = TAB_COUNT - 1;
        if (next >= TAB_COUNT) next = 0;
        tabIndex = next;
        contentFocus = 0;
        render();
    }

    private void enterContent() {
        String[] items = currentItems();
        if (items.length == 0) {
            return;
        }
        layer = LAYER_CONTENT;
        contentFocus = 0;
        render();
    }

    private void moveContentFocus(int delta) {
        String[] items = currentItems();
        if (items.length == 0) {
            return;
        }
        int next = contentFocus + delta;
        if (next < 0) next = 0;
        if (next >= items.length) next = items.length - 1;
        if (next == contentFocus) {
            return;
        }
        contentFocus = next;
        render();
    }

    private void pickCurrent() {
        String[] items = currentItems();
        if (contentFocus >= 0 && contentFocus < items.length && callback != null) {
            callback.onSymbolPicked(items[contentFocus]);
        }
        dismiss();
    }

    // --- Data ---

    private boolean isListMode() {
        return tabIndex == TAB_KAO || tabIndex == TAB_PHRASE;
    }

    private String[] currentItems() {
        switch (tabIndex) {
            case TAB_CN: return CN_SYMBOLS;
            case TAB_EN: return EN_SYMBOLS;
            case TAB_KAO: return KAOMOJI;
            case TAB_GREEK: return GREEK_SYMBOLS;
            case TAB_PHRASE: return phraseItems.toArray(new String[0]);
            default: return new String[0];
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

    // --- Rendering ---

    private void render() {
        renderTabBar();
        renderContent();
    }

    private void renderTabBar() {
        tabBar.removeAllViews();
        for (int i = 0; i < TAB_COUNT; i++) {
            TextView tv = new TextView(context);
            tv.setText(TAB_LABELS[i]);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setPadding(6, 8, 6, 8);
            tv.setTextSize(13);
            tv.setMinimumHeight(0);
            if (layer == LAYER_TAB && i == tabIndex) {
                tv.setBackgroundResource(R.drawable.list_focus_bg);
                tv.setTextColor(Color.BLACK);
                tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
            } else if (layer == LAYER_CONTENT && i == tabIndex) {
                tv.setBackgroundColor(Color.TRANSPARENT);
                tv.setTextColor(context.getResources().getColor(R.color.candidate_focus));
                tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
            } else {
                tv.setBackgroundColor(Color.TRANSPARENT);
                tv.setTextColor(Color.BLACK);
                tv.setTypeface(tv.getTypeface(), Typeface.NORMAL);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tabBar.addView(tv, lp);
        }
    }

    private void renderContent() {
        String[] items = currentItems();
        boolean listMode = isListMode();

        grid.setVisibility(!listMode && items.length > 0 ? View.VISIBLE : View.GONE);
        list.setVisibility(listMode && items.length > 0 ? View.VISIBLE : View.GONE);
        boolean showEmpty = items.length == 0;
        emptyHint.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (showEmpty) {
            emptyHint.setText(context.getString(R.string.symbol_phrase_empty));
        }

        if (listMode) {
            renderList(items);
        } else {
            renderGrid(items);
        }
    }

    private void renderGrid(String[] items) {
        final String[] data = items;
        final int focusRef = contentFocus;
        final boolean contentLayer = (layer == LAYER_CONTENT);
        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return data.length; }
            @Override public Object getItem(int p) { return data[p]; }
            @Override public long getItemId(int p) { return p; }

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
                tv.setText(data[position]);
                tv.setTextSize(14);
                if (contentLayer && position == focusRef) {
                    tv.setBackgroundResource(R.drawable.list_focus_bg);
                    tv.setTextColor(Color.BLACK);
                } else {
                    tv.setBackgroundColor(Color.TRANSPARENT);
                    tv.setTextColor(Color.BLACK);
                }
                return tv;
            }
        });
        grid.setSelection(contentLayer ? focusRef : 0);
    }

    private void renderList(String[] items) {
        final String[] data = items;
        final int focusRef = contentFocus;
        final boolean contentLayer = (layer == LAYER_CONTENT);
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return data.length; }
            @Override public Object getItem(int p) { return data[p]; }
            @Override public long getItemId(int p) { return p; }

            @Override
            public View getView(int position, View convertView, ViewGroup parentView) {
                TextView tv;
                if (convertView instanceof TextView) {
                    tv = (TextView) convertView;
                } else {
                    tv = new TextView(context);
                    tv.setGravity(Gravity.CENTER);
                    tv.setPadding(20, 12, 20, 12);
                    tv.setTextSize(14);
                    tv.setSingleLine(false);
                }
                tv.setText(data[position]);
                if (contentLayer && position == focusRef) {
                    tv.setBackgroundResource(R.drawable.list_focus_bg);
                    tv.setTextColor(Color.BLACK);
                } else {
                    tv.setBackgroundColor(Color.TRANSPARENT);
                    tv.setTextColor(Color.BLACK);
                }
                return tv;
            }
        });
        list.setSelection(contentLayer ? focusRef : 0);
    }
}
