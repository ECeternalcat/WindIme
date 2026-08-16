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

        TextView changelog = findViewById(R.id.about_changelog);
        changelog.setOnClickListener(view -> viewChangelog());

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

    /**
     * Fetch the latest release and show its notes. When the release is newer
     * than the running build this is the normal update dialog (working
     * download button); when it is the current (or an older) version it is a
     * read-only changelog viewer with the download button disabled.
     */
    private void viewChangelog() {
        if (updateCheckRunning) {
            return;
        }
        updateCheckRunning = true;
        Toast.makeText(this, R.string.update_fetching, Toast.LENGTH_SHORT).show();
        UpdateChecker.checkAsync(this, new UpdateChecker.Callback() {
            @Override
            public void onResult(UpdateInfo release, boolean isNewer) {
                updateCheckRunning = false;
                if (isFinishing()) {
                    return;
                }
                if (isNewer) {
                    showUpdateDialog(release);
                } else {
                    showChangelogDialog(release);
                }
            }

            @Override
            public void onError() {
                updateCheckRunning = false;
                if (isFinishing()) {
                    return;
                }
                Toast.makeText(AboutActivity.this,
                        R.string.update_fetch_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showUpdateDialog(final UpdateInfo release) {
        buildNotesDialog(release, false);
    }

    /** Read-only variant: same layout as the update dialog, download disabled. */
    private void showChangelogDialog(final UpdateInfo release) {
        buildNotesDialog(release, true);
    }

    /**
     * Japanese flip-phone dialog style: scrollable notes plus two stacked
     * full-width bar buttons (list_selector focus look, black text). In
     * read-only mode the download bar is greyed out and disabled, and focus
     * starts on the close bar.
     */
    private void buildNotesDialog(final UpdateInfo release, final boolean readOnly) {
        android.view.View body = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_update, null);
        TextView notes = body.findViewById(R.id.update_notes);
        String formatted = com.garaho.ime.settings.update.ReleaseNotesFormatter
                .format(release.notes);
        notes.setText(formatted.isEmpty()
                ? getString(R.string.update_changelog_empty) : formatted);

        final TextView positive = body.findViewById(R.id.update_btn_positive);
        final TextView negative = body.findViewById(R.id.update_btn_negative);
        negative.setText(readOnly
                ? R.string.update_changelog_close : R.string.update_later);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(readOnly
                        ? getString(R.string.update_changelog_title, release.versionName())
                        : getString(R.string.update_available_title)
                                + " v" + release.versionName())
                .setView(body)
                .create();
        negative.setOnClickListener(view -> dialog.dismiss());
        if (readOnly) {
            positive.setEnabled(false);
            positive.setTextColor(0xFF9E9E9E);
        } else {
            positive.setOnClickListener(view -> openUrl(release.downloadUrl()));
        }
        dialog.setOnShowListener(ignored ->
                (readOnly ? negative : positive).requestFocus());
        dialog.show();
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
