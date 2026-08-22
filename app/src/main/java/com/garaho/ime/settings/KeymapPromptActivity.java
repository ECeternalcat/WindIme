package com.garaho.ime.settings;

import com.garaho.ime.R;
import com.garaho.ime.keymap.KeymapSlots;
import com.garaho.ime.ui.SetupWizardActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

/**
 * Transparent host for the first-use keymap-setup prompt. Showing this prompt
 * as an {@link Activity} (rather than a {@code TYPE_SYSTEM_ALERT} dialog from
 * the IME service) avoids the {@code SYSTEM_ALERT_WINDOW} permission entirely:
 * the activity owns a normal window token, so it works on every API level with
 * no special permission and no {@code BadTokenException}.
 *
 * <p>Launched once by {@code GarahoImeService} when the user has no configured
 * keymap slot and has not dismissed the prompt. "Create" opens the calibration
 * wizard on slot 1; "Later" permanently dismisses it; BACK just closes this
 * prompt (it may reappear on a later fresh session).
 */
public final class KeymapPromptActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.keymap_prompt_title)
                .setMessage(R.string.keymap_prompt_message)
                .setPositiveButton(R.string.keymap_prompt_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new GarahoPrefs(KeymapPromptActivity.this).setKeymapPromptDismissed(true);
                        Intent intent = new Intent(KeymapPromptActivity.this,
                                com.garaho.ime.ui.SetupWizardIntroActivity.class);
                        intent.putExtra(SetupWizardActivity.EXTRA_TARGET_SLOT, KeymapSlots.USER_MIN);
                        startActivity(intent);
                        finish();
                    }
                })
                .setNegativeButton(R.string.keymap_prompt_no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new GarahoPrefs(KeymapPromptActivity.this).setKeymapPromptDismissed(true);
                        finish();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        // BACK / outside: do not permanently dismiss.
                        finish();
                    }
                })
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }
}
