package com.garaho.ime.settings;

import com.garaho.ime.R;
import com.garaho.ime.keymap.KeyMapper;
import com.garaho.ime.rime.RimeMaintenance;
import com.garaho.ime.user.PhraseStore;
import com.garaho.ime.user.UserDictionary;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

/**
 * Reset actions (design doc §2.5 / improvement doc §5): five independent,
 * separately-confirmed destructive operations so the user can clear exactly
 * what they mean to — keymap, Rime learning, user dictionary, phrases, or
 * everything at once.
 */
public class ResetSettingsActivity extends BaseMenuActivity {

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String[] items = new String[] {
                getString(R.string.reset_keymap),
                getString(R.string.reset_user_data),
                getString(R.string.reset_user_dict),
                getString(R.string.reset_phrases),
                getString(R.string.reset_all),
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
                                RimeMaintenance.enqueue(ResetSettingsActivity.this,
                                        RimeMaintenance.Action.CLEAR_LEARNING);
                            }
                        });
                        break;
                    case 2:
                        confirm(R.string.reset_user_dict_confirm, new Runnable() {
                            @Override public void run() {
                                UserDictionary.get(ResetSettingsActivity.this).clear();
                            }
                        });
                        break;
                    case 3:
                        confirm(R.string.reset_phrases_confirm, new Runnable() {
                            @Override public void run() {
                                PhraseStore.get(ResetSettingsActivity.this).clear();
                            }
                        });
                        break;
                    case 4:
                        confirm(R.string.reset_all_confirm, new Runnable() {
                            @Override public void run() {
                                new GarahoPrefs(ResetSettingsActivity.this).clearAll();
                                new KeyMapper(ResetSettingsActivity.this).resetToFactory();
                                UserDictionary.get(ResetSettingsActivity.this).clear();
                                PhraseStore.get(ResetSettingsActivity.this).clear();
                                RimeMaintenance.enqueue(ResetSettingsActivity.this,
                                        RimeMaintenance.Action.CLEAR_LEARNING);
                                Toast.makeText(ResetSettingsActivity.this,
                                        R.string.reset_all_done, Toast.LENGTH_SHORT).show();
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

    @Override
    protected int getTitleRes() {
        return R.string.settings_reset;
    }
}
