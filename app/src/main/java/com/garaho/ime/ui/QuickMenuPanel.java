package com.garaho.ime.ui;

import com.garaho.ime.R;
import com.garaho.ime.keymap.InputAction;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

/** Modal D-pad menu shown when Android assigns the flip-phone left soft key to MENU. */
public final class QuickMenuPanel {

    public interface Callback {
        void onQuickMenuItem(int position);
    }

    private final Context context;
    private final Callback callback;
    private ViewGroup parent;
    private View root;
    private TextView title;
    private ListView list;
    private boolean showing;
    private String[] items = new String[0];
    private boolean[] checkedItems;
    private int selectedIndex;

    public QuickMenuPanel(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    public boolean isShowing() {
        return showing;
    }

    public void show(ViewGroup parent, int titleRes, String[] menuItems) {
        show(parent, titleRes, menuItems, null);
    }

    public void showChecked(ViewGroup parent, int titleRes, String[] menuItems,
            boolean[] checked) {
        show(parent, titleRes, menuItems, checked);
    }

    private void show(ViewGroup parent, int titleRes, String[] menuItems, boolean[] checked) {
        this.parent = parent;
        if (root == null) {
            root = LayoutInflater.from(context).inflate(R.layout.view_quick_menu, parent, false);
            title = root.findViewById(R.id.quick_menu_title);
            list = root.findViewById(R.id.quick_menu_list);
            // Prevent the ListView from stealing D-pad focus (same rationale as
            // SymbolPanel: manual focus management in handleAction()).
            list.setFocusable(false);
            list.setFocusableInTouchMode(false);
            list.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            // Pin to the bottom so the menu sits over the candidate strip in
            // fullscreen mode (rootContainer is full-screen there).
            lp.gravity = Gravity.BOTTOM;
            parent.addView(root, lp);
        }
        items = menuItems == null ? new String[0] : menuItems;
        checkedItems = checked != null && checked.length == items.length ? checked.clone() : null;
        selectedIndex = 0;
        title.setText(titleRes);
        renderItems();
        root.setVisibility(View.VISIBLE);
        root.bringToFront();
        parent.requestLayout();
        showing = true;
    }

    public void dismiss() {
        showing = false;
        if (root != null) {
            root.setVisibility(View.GONE);
        }
        if (parent != null) {
            parent.requestLayout();
        }
    }

    public void setSelection(int position) {
        if (items.length > 0) {
            selectedIndex = Math.max(0, Math.min(position, items.length - 1));
            renderItems();
        }
    }

    public boolean handleAction(InputAction action) {
        if (!showing || items.length == 0) {
            return false;
        }
        switch (action) {
            case NAV_UP:
                setSelection(selectedIndex - 1);
                return true;
            case NAV_DOWN:
                setSelection(selectedIndex + 1);
                return true;
            case CONFIRM_SELECTION:
                if (callback != null) {
                    callback.onQuickMenuItem(selectedIndex);
                }
                return true;
            default:
                return true;
        }
    }

    private void renderItems() {
        if (list == null) {
            return;
        }
        final int selected = selectedIndex;
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() {
                return items.length;
            }

            @Override public Object getItem(int position) {
                return items[position];
            }

            @Override public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView row;
                if (checkedItems != null) {
                    CheckedTextView checkedRow;
                    if (convertView instanceof CheckedTextView) {
                        checkedRow = (CheckedTextView) convertView;
                    } else {
                        checkedRow = (CheckedTextView) LayoutInflater.from(context).inflate(
                                android.R.layout.simple_list_item_multiple_choice, parent, false);
                        checkedRow.setMinHeight(dp(48));
                        checkedRow.setTextSize(17);
                    }
                    checkedRow.setChecked(checkedItems[position]);
                    row = checkedRow;
                } else if (convertView instanceof TextView
                        && !(convertView instanceof CheckedTextView)) {
                    row = (TextView) convertView;
                } else {
                    row = new TextView(context);
                    configureRow(row);
                }
                row.setText(items[position]);
                if (position == selected) {
                    row.setBackgroundResource(R.drawable.list_focus_bg);
                    row.setTextColor(context.getResources().getColor(R.color.primary_text));
                } else {
                    row.setBackgroundColor(Color.TRANSPARENT);
                    row.setTextColor(context.getResources().getColor(R.color.primary_text));
                }
                return row;
            }
        });
        list.clearChoices();
        list.setSelection(selectedIndex);
    }

    private void configureRow(TextView row) {
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinHeight(dp(48));
        row.setPadding(dp(20), dp(10), dp(20), dp(10));
        row.setTextSize(17);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
