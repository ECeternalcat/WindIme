package com.garaho.ime.settings;

import com.garaho.ime.BuildConfig;
import com.garaho.ime.R;
import com.garaho.ime.settings.update.UpdateChecker;
import com.garaho.ime.settings.update.UpdateInfo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

/** Project information and license entry points, fully operable by D-pad. */
public class AboutActivity extends Activity {

    private int noticeClickCount;
    private GarahoPrefs prefs;
    private boolean updateCheckRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        prefs = new GarahoPrefs(this);

        TextView version = findViewById(R.id.about_version);
        version.setText(getString(R.string.about_version_format,
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));

        TextView notice = findViewById(R.id.about_notice);
        notice.setOnClickListener(view -> {
            noticeClickCount++;
            if (noticeClickCount == 5) {
                noticeClickCount = 0;
                startActivity(new Intent(this, EasterEggActivity.class));
            }
        });

        TextView update = findViewById(R.id.about_update);
        update.setOnClickListener(view -> checkForUpdate(true));

        TextView source = findViewById(R.id.about_source);
        source.setOnClickListener(view -> openSourceCode());

        TextView licenses = findViewById(R.id.about_licenses);
        licenses.setOnClickListener(view -> startActivity(
                new Intent(this, OpenSourceLicensesActivity.class)));
        notice.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Silent daily check: only speaks up when a newer release exists.
        if (UpdateChecker.shouldAutoCheck(prefs)) {
            checkForUpdate(false);
        }
    }

    private void checkForUpdate(final boolean manual) {
        if (updateCheckRunning) {
            return;
        }
        updateCheckRunning = true;
        if (manual) {
            Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show();
        }
        UpdateChecker.checkAsync(this, new UpdateChecker.Callback() {
            @Override
            public void onResult(UpdateInfo release, boolean isNewer) {
                updateCheckRunning = false;
                if (isFinishing()) {
                    return;
                }
                if (isNewer) {
                    showUpdateDialog(release);
                } else if (manual) {
                    Toast.makeText(AboutActivity.this,
                            R.string.update_already_latest, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError() {
                updateCheckRunning = false;
                if (isFinishing()) {
                    return;
                }
                if (manual) {
                    Toast.makeText(AboutActivity.this,
                            R.string.update_check_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showUpdateDialog(final UpdateInfo release) {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.update_available_title)
                        + " v" + release.versionName())
                .setMessage(release.notes == null || release.notes.isEmpty()
                        ? getString(R.string.update_download)
                        : release.notes)
                .setPositiveButton(R.string.update_download, null)
                .setNegativeButton(R.string.update_later, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    openUrl(release.downloadUrl());
                }));
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show();
        }
    }

    private void openSourceCode() {
        openUrl(getString(R.string.about_source_url));
    }
}
