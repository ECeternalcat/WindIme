package com.garaho.ime.settings;

import com.garaho.ime.R;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Displays a bundled license as D-pad-scrollable plain text. */
public class LicenseTextActivity extends Activity {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ASSET = "asset";
    public static final String EXTRA_ATTRIBUTION = "attribution";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license_text);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String asset = getIntent().getStringExtra(EXTRA_ASSET);
        int attributionRes = getIntent().getIntExtra(EXTRA_ATTRIBUTION, 0);

        ((TextView) findViewById(R.id.license_title)).setText(title);
        TextView text = findViewById(R.id.license_text);
        String attribution = attributionRes == 0 ? "" : getString(attributionRes) + "\n\n";
        text.setText(attribution + readAsset(asset));

        ScrollView scroll = findViewById(R.id.license_scroll);
        scroll.requestFocus();
    }

    private String readAsset(String path) {
        if (path == null) {
            return getString(R.string.license_load_failed);
        }
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append('\n');
            }
            return result.toString();
        } catch (IOException e) {
            return getString(R.string.license_load_failed);
        }
    }
}
