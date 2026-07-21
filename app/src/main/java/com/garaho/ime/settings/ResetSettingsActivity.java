package com.garaho.ime.settings;

import com.garaho.ime.R;
import com.garaho.ime.keymap.KeyMapper;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.AdapterView;

import java.io.File;

/**
 * Reset actions (design doc §2.5): restore the factory keymap and/or wipe the
 * RIME user-data directory (learned words, build cache). Each action confirms
 * before destructive changes.
 */
public class ResetSettingsActivity extends BaseMenuActivity {

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String[] items = new String[] {
                getString(R.string.reset_keymap),
                getString(R.string.reset_user_data),
        };
        setMenuItems(items, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        confirm(R.string.reset_keymap_confirm, new Runnable() {
                            @Override public void run() {
                                new KeyMapper(ResetSettingsActivity.this).resetToFactory();
                            }
                        });
                        break;
                    case 1:
                        confirm(R.string.reset_user_data_confirm, new Runnable() {
                            @Override public void run() {
                                deleteRecursive(new File(getFilesDir(), "rime_user"));
                            }
                        });
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void confirm(int messageRes, final Runnable onConfirm) {
        new AlertDialog.Builder(this)
                .setMessage(messageRes)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onConfirm.run();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteRecursive(c);
                }
            }
        }
        file.delete();
    }

    @Override
    protected int getTitleRes() {
        return R.string.settings_reset;
    }
}
