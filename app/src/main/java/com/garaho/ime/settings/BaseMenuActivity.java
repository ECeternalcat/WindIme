package com.garaho.ime.settings;

import com.garaho.ime.R;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Base for 0-Touch settings pages (design doc §3): a bold title, a D-Pad
 * focusable {@link ListView} with a high-contrast selector, and a fixed
 * navigation hint footer. Subclasses populate the list.
 */
public abstract class BaseMenuActivity extends Activity {

    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        listView = findViewById(R.id.menu_list);
        TextView title = findViewById(R.id.menu_title);
        title.setText(getTitleRes());
    }

    protected ListView getListView() {
        return listView;
    }

    protected void setMenuItems(String[] items, android.widget.AdapterView.OnItemClickListener listener) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.list_item_menu, items);
        // Remember the focused row across rebuilds (value-cycling rows call
        // rebuild() on every OK) until the page is destroyed, so the user can
        // tap OK repeatedly without scrolling back down each time.
        int keep = listView.getSelectedItemPosition();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(listener);
        if (items.length > 0) {
            int target = keep == android.widget.AdapterView.INVALID_POSITION
                    || keep >= items.length ? 0 : keep;
            listView.setSelection(target);
            listView.requestFocus();
        }
    }

    /**
     * Wrap D-pad focus cyclically: at the top, UP jumps to the last row; at the
     * bottom, DOWN jumps to the first. Intercepted in {@code dispatchKeyEvent}
     * so it works regardless of whether the ListView consumes the boundary key.
     */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && listView != null) {
            int count = listView.getCount();
            if (count > 0) {
                int pos = listView.getSelectedItemPosition();
                if (pos != android.widget.AdapterView.INVALID_POSITION) {
                    int keyCode = event.getKeyCode();
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP && pos == 0) {
                        listView.setSelection(count - 1);
                        return true;
                    }
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN && pos == count - 1) {
                        listView.setSelection(0);
                        return true;
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    protected abstract int getTitleRes();
}
