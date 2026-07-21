package com.garaho.ime.settings;

import com.garaho.ime.R;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Canned-phrase manager (design doc §2.4). Scaffold for editing quick-insert
 * phrases; full CRUD lands in a later phase.
 */
public class PhraseActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_placeholder);
        ((TextView) findViewById(R.id.menu_title)).setText(R.string.settings_phrase);
        ((TextView) findViewById(R.id.placeholder_text)).setText(R.string.phrase_placeholder);
    }
}
