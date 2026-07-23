package com.garaho.ime.feedback;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.garaho.ime.settings.GarahoPrefs;

/**
 * Physical key-press feedback (design doc §2.1 / SettingsPage {@code 按键反馈}).
 *
 * <p>Single owner of haptic/audio feedback so the system key click is not
 * duplicated: {@code GarahoImeService} consumes every handled {@code KeyEvent}
 * by returning {@code true} from {@code onKeyDown}, which suppresses the
 * platform's default key click. This class is then the only source of the
 * user-selected vibration/sound.
 *
 * <p>Three modes, mirroring {@link GarahoPrefs}: vibrate / sound / none. The
 * active mode is refreshed by the service on each input start so a settings
 * change applies to the next input field without restarting the IME.
 *
 * <p>API 21 compatible: {@code Vibrator.vibrate(long)} on pre-O (guarded) and
 * {@code VibrationEffect.createOneShot} on O+.
 */
public final class KeyFeedback {

    private static final long VIBRATE_MS = 18L;

    private final Vibrator vibrator;
    private final AudioManager audioManager;
    private String mode = GarahoPrefs.FEEDBACK_VIBRATE;

    public KeyFeedback(Context context) {
        Context app = context.getApplicationContext();
        Object vib = app.getSystemService(Context.VIBRATOR_SERVICE);
        vibrator = vib instanceof Vibrator ? (Vibrator) vib : null;
        Object aud = app.getSystemService(Context.AUDIO_SERVICE);
        audioManager = aud instanceof AudioManager ? (AudioManager) aud : null;
    }

    public void setMode(String mode) {
        this.mode = mode == null ? GarahoPrefs.FEEDBACK_VIBRATE : mode;
    }

    /** Fire the currently configured feedback. Safe to call on the main thread. */
    public void perform() {
        if (GarahoPrefs.FEEDBACK_SOUND.equals(mode)) {
            if (audioManager != null) {
                audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK);
            }
        } else if (GarahoPrefs.FEEDBACK_VIBRATE.equals(mode)) {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(
                            VIBRATE_MS, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(VIBRATE_MS);
                }
            }
        }
        // FEEDBACK_NONE: intentionally no effect.
    }
}
