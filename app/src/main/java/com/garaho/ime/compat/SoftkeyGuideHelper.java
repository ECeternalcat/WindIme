package com.garaho.ime.compat;

import android.content.Context;
import android.view.Window;

import java.lang.reflect.Method;

/**
 * Reflective wrapper around the Kyocera/SoftBank vendor Softkey Guide
 * ({@code com.nextfp.android.util.NfpSoftkeyGuide}), present only on devices
 * like the NP701KC. It lets WindIme set the center-softkey label (e.g. "完成")
 * the same way iWnn does, so the Softkey Guide stops showing a placeholder
 * square while a vendor app expects a "complete" action.
 *
 * <p>All access is reflective and null-tolerant: on ordinary Android the vendor
 * class is absent and every call is a silent no-op. The shared library is
 * declared in the manifest with {@code required=false}, so the class loads into
 * the app classloader on vendor devices but the APK still installs elsewhere.
 *
 * <p>Per the np701kc.md reverse-engineering report: iWnn obtains the guide from
 * the IME's own {@link Window} via {@code NfpSoftkeyGuide.getSoftkeyGuide(window)},
 * then calls {@code setText(INDEX_CSK, text)} followed by {@code invalidate()}.
 */
public final class SoftkeyGuideHelper {

    private static final String CLASS_NAME = "com.nextfp.android.util.NfpSoftkeyGuide";

    /** Center soft key (the OK position). Matches NfpSoftkeyGuide.INDEX_CSK. */
    public static final int INDEX_CSK = 0;
    /** Left soft key. Matches NfpSoftkeyGuide.INDEX_SK1. */
    public static final int INDEX_SK1 = 1;
    /** Right soft key. Matches NfpSoftkeyGuide.INDEX_SK2. */
    public static final int INDEX_SK2 = 2;

    private final Class<?> guideClass;

    private SoftkeyGuideHelper(Class<?> guideClass) {
        this.guideClass = guideClass;
    }

    /** @return a helper if the vendor class is available on this device, else {@code null}. */
    public static SoftkeyGuideHelper create(Context context) {
        if (context == null) {
            return null;
        }
        try {
            Class<?> c = Class.forName(CLASS_NAME, false, context.getClassLoader());
            // Sanity-check the entry point so we know this is really the vendor class.
            c.getMethod("getSoftkeyGuide", Window.class);
            return new SoftkeyGuideHelper(c);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Set the center softkey label on the given window and refresh the guide.
     *
     * @param window the IME's Window (e.g. {@code InputMethodService.getWindow().getWindow()})
     * @param label  the label text, or empty/null to clear
     * @return {@code true} if the label was applied
     */
    public boolean setCenterLabel(Window window, CharSequence label) {
        return setLabel(window, INDEX_CSK, label);
    }

    /**
     * Set the label for any softkey index and refresh the guide.
     *
     * @param window the IME's Window
     * @param index  one of {@link #INDEX_CSK}, {@link #INDEX_SK1}, {@link #INDEX_SK2}
     * @param label  the label text, or empty/null to clear
     * @return {@code true} if the label was applied
     */
    public boolean setLabel(Window window, int index, CharSequence label) {
        if (window == null) {
            return false;
        }
        try {
            java.lang.reflect.Method get = guideClass.getMethod("getSoftkeyGuide", Window.class);
            Object guide = get.invoke(null, window);
            if (guide == null) {
                return false;
            }
            guideClass.getMethod("setText", int.class, CharSequence.class)
                    .invoke(guide, index, label == null ? "" : label);
            guideClass.getMethod("invalidate").invoke(guide);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Set all three softkey labels in one call, with a single {@code invalidate()}.
     * More efficient than three separate {@link #setLabel} calls.
     *
     * @param window the IME's Window
     * @param sk1    left softkey label (null/empty to clear)
     * @param sk2    right softkey label (null/empty to clear)
     * @param csk    center softkey label (null/empty to clear)
     * @return {@code true} if labels were applied
     */
    public boolean setAllLabels(Window window, CharSequence sk1, CharSequence sk2, CharSequence csk) {
        if (window == null) {
            return false;
        }
        try {
            java.lang.reflect.Method get = guideClass.getMethod("getSoftkeyGuide", Window.class);
            Object guide = get.invoke(null, window);
            if (guide == null) {
                return false;
            }
            java.lang.reflect.Method setText = guideClass.getMethod("setText", int.class, CharSequence.class);
            setText.invoke(guide, INDEX_SK1, sk1 == null ? "" : sk1);
            setText.invoke(guide, INDEX_SK2, sk2 == null ? "" : sk2);
            setText.invoke(guide, INDEX_CSK, csk == null ? "" : csk);
            guideClass.getMethod("invalidate").invoke(guide);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Best-effort SHF33/NFP floating guide label. The Sharp framework has
     * shipped more than one signature, so probe the available method instead
     * of linking against a vendor-only stub.
     */
    public boolean setFloatingGuideAreaLabel(Window window, int index, CharSequence label) {
        if (window == null) {
            return false;
        }
        try {
            Method get = guideClass.getMethod("getSoftkeyGuide", Window.class);
            Object guide = get.invoke(null, window);
            if (guide == null) {
                return false;
            }
            for (Method method : guide.getClass().getMethods()) {
                if (!"setFloatingGuideAreaText".equals(method.getName())) {
                    continue;
                }
                Class<?>[] p = method.getParameterTypes();
                if (p.length == 2 && (p[0] == int.class || p[0] == Integer.class)) {
                    method.invoke(guide, index, label == null ? "" : label);
                    invalidate(guide);
                    return true;
                }
                if (p.length == 1 && CharSequence.class.isAssignableFrom(p[0])) {
                    method.invoke(guide, label == null ? "" : label);
                    invalidate(guide);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void invalidate(Object guide) throws Exception {
        guide.getClass().getMethod("invalidate").invoke(guide);
    }
}
