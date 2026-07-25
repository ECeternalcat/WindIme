package com.garaho.ime.settings;

import com.garaho.ime.R;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Base for 0-Touch settings pages (design doc §3): a title bar and a D-Pad
 * focusable {@link ListView} with a high-contrast selector. Navigation wraps
 * around (UP at the first item jumps to the last, DOWN at the last to first).
 * Subclasses populate the list.
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
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(listener);
        listView.setOnKeyListener(new android.view.View.OnKeyListener() {
            @Override
            public boolean onKey(android.view.View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                int count = listView.getCount();
                if (count == 0) {
                    return false;
                }
                int current = listView.getSelectedItemPosition();
                if (current == AdapterView.INVALID_POSITION) {
                    return false;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && current == 0) {
                    listView.setSelection(count - 1);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && current == count - 1) {
                    listView.setSelection(0);
                    return true;
                }
                return false;
            }
        });
        if (items.length > 0) {
            listView.setSelection(0);
            listView.requestFocus();
        }
    }

    protected abstract int getTitleRes();
}
