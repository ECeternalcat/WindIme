package com.garaho.ime.settings;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import com.garaho.ime.R;
import com.garaho.ime.rime.RimeData;
import com.garaho.ime.rime.RimeMaintenance;
import com.garaho.ime.rime.RimeRuntimeStatus;

import java.io.File;
import java.util.Locale;

/** D-Pad deployment status and deferred Rime maintenance management. */
public final class RimeSettingsActivity extends BaseMenuActivity {

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        RimeRuntimeStatus.Snapshot status = RimeRuntimeStatus.get(this);
        RimeData data = new RimeData(this);
        File userDir = data.getUserDir();
        String pending = RimeMaintenance.pendingSummary(this);
        if (pending.isEmpty()) {
            pending = getString(R.string.rime_none);
        }
        String[] items = {
                getString(R.string.rime_status_title) + ": " + stateLabel(status)
                        + (status.detail.isEmpty() ? "" : " - " + status.detail),
                getString(R.string.rime_data_version) + ": " + RimeData.DATA_VERSION,
                getString(R.string.rime_dict_set) + ": " + getString(R.string.rime_dict_set_value),
                getString(R.string.rime_shared_size) + ": "
                        + formatBytes(RimeMaintenance.sizeOf(data.getSharedDir())),
                getString(R.string.rime_build_size) + ": "
                        + formatBytes(RimeMaintenance.sizeOf(new File(userDir, "build"))),
                getString(R.string.rime_learning_size) + ": "
                        + formatBytes(RimeMaintenance.learningSize(userDir)),
                getString(R.string.rime_pending) + ": " + pending,
                getString(R.string.rime_redeploy),
                getString(R.string.rime_clear_build),
                getString(R.string.rime_clear_learning),
        };
        setMenuItems(items, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 7) {
                    confirm(R.string.rime_redeploy_confirm, RimeMaintenance.Action.REDEPLOY);
                } else if (position == 8) {
                    confirm(R.string.rime_clear_build_confirm, RimeMaintenance.Action.CLEAR_BUILD_CACHE);
                } else if (position == 9) {
                    confirm(R.string.rime_clear_learning_confirm, RimeMaintenance.Action.CLEAR_LEARNING);
                }
            }
        });
    }

    private void confirm(int message, final RimeMaintenance.Action action) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        RimeMaintenance.enqueue(RimeSettingsActivity.this, action);
                        Toast.makeText(RimeSettingsActivity.this, R.string.rime_queued, Toast.LENGTH_LONG).show();
                        rebuild();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String stateLabel(RimeRuntimeStatus.Snapshot snapshot) {
        switch (snapshot.state) {
            case PREPARING: return getString(R.string.rime_status_preparing_short);
            case READY: return getString(R.string.rime_status_ready_short);
            case FAILED: return getString(R.string.rime_status_failed_short);
            case LIGHTWEIGHT:
            default: return getString(R.string.rime_status_lightweight_short);
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Override
    protected int getTitleRes() {
        return R.string.settings_rime;
    }
}
