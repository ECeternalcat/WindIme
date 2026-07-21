package com.garaho.ime.settings;

import com.garaho.ime.R;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * User-dictionary manager (design doc §2.3). Scaffold for adding/editing user
 * words; full CRUD lands in a later phase.
 */
public class UserDictActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_placeholder);
        ((TextView) findViewById(R.id.menu_title)).setText(R.string.settings_user_dict);
        ((TextView) findViewById(R.id.placeholder_text)).setText(R.string.user_dict_placeholder);
    }
}
