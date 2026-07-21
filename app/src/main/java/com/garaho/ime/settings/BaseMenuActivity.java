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
        TextView hint = findViewById(R.id.menu_hint);
        hint.setText(getHintRes());
    }

    protected ListView getListView() {
        return listView;
    }

    protected void setMenuItems(String[] items, android.widget.AdapterView.OnItemClickListener listener) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.list_item_menu, items);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(listener);
        if (items.length > 0) {
            listView.setSelection(0);
            listView.requestFocus();
        }
    }

    protected abstract int getTitleRes();

    protected int getHintRes() {
        return R.string.menu_hint_nav;
    }
}
