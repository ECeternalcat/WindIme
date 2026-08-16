package com.garaho.ime.settings;

import com.garaho.ime.BuildConfig;
import com.garaho.ime.R;
import com.garaho.ime.settings.update.UpdateChecker;
import com.garaho.ime.settings.update.UpdateInfo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
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
     * full-width bar buttons. D-pad selection is managed manually (Android
     * view focus proved unreliable inside an AlertDialog on these devices):
     * UP/DOWN page through the notes and, once pageScroll reports the end,
     * move the selection; LEFT/RIGHT always move the selection; OK activates
     * the selected bar. The selected bar is painted with the standard
     * list_focus_bg focus look. In read-only mode the download bar is greyed
     * out and the selection stays on Close.
     */
    private void buildNotesDialog(final UpdateInfo release, final boolean readOnly) {
        android.view.View body = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_update, null);
        final TextView notes = body.findViewById(R.id.update_notes);
        final android.widget.ScrollView notesScroll = body.findViewById(R.id.update_notes_scroll);
        String formatted = com.garaho.ime.settings.update.ReleaseNotesFormatter
                .format(release.notes);
        notes.setText(formatted.isEmpty()
                ? getString(R.string.update_changelog_empty) : formatted);
        // Cap the ScrollView viewport (never the TextView: setMaxHeight on the
        // text view clips overflow instead of letting the ScrollView scroll).
        final int cap = (int) (getResources().getDisplayMetrics().heightPixels * 0.40f);
        int widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(
                getResources().getDisplayMetrics().widthPixels,
                android.view.View.MeasureSpec.AT_MOST);
        notes.measure(widthSpec, android.view.View.MeasureSpec.makeMeasureSpec(
                0, android.view.View.MeasureSpec.UNSPECIFIED));
        notesScroll.getLayoutParams().height =
                Math.min(notes.getMeasuredHeight(), cap);

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

        // Manually selected bar: [0]=download, [1]=close. Read-only keeps 0.
        final TextView[] bars = readOnly
                ? new TextView[] { negative }
                : new TextView[] { positive, negative };
        final int[] selected = { 0 };

        dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface d, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        // pageScroll returns false once the top is reached,
                        // then the key moves the selection instead.
                        if (notesScroll.pageScroll(android.view.View.FOCUS_UP)) {
                            return true;
                        }
                        moveSelection(bars, selected, -1);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (notesScroll.pageScroll(android.view.View.FOCUS_DOWN)) {
                            return true;
                        }
                        moveSelection(bars, selected, 1);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        moveSelection(bars, selected, -1);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        moveSelection(bars, selected, 1);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                        bars[selected[0]].performClick();
                        return true;
                    default:
                        return false;
                }
            }
        });
        dialog.show();
        paintSelection(bars, selected[0]);
    }

    private static boolean moveSelection(TextView[] bars, int[] selected, int delta) {
        int next = Math.max(0, Math.min(bars.length - 1, selected[0] + delta));
        if (next == selected[0]) {
            return false;
        }
        selected[0] = next;
        paintSelection(bars, next);
        return true;
    }

    private static void paintSelection(TextView[] bars, int index) {
        for (int i = 0; i < bars.length; i++) {
            TextView bar = bars[i];
            if (i == index) {
                bar.setBackgroundResource(R.drawable.list_focus_bg);
                bar.setTypeface(bar.getTypeface(), android.graphics.Typeface.BOLD);
            } else {
                bar.setBackgroundResource(0);
                bar.setTypeface(bar.getTypeface(), android.graphics.Typeface.NORMAL);
            }
        }
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
