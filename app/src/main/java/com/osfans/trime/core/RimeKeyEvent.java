package com.osfans.trime.core;

/**
 * Key event descriptor (JNI contract: ctor {@code (int value, int modifiers, String repr)}).
 *
 * <p>The native {@code JNI_OnLoad} resolves this class via {@code FindClass} so it
 * must exist even though WindIme does not call its native methods.
 */
public final class RimeKeyEvent {
    public final int value;
    public final int modifiers;
    public final String repr;

    public RimeKeyEvent(int value, int modifiers, String repr) {
        this.value = value;
        this.modifiers = modifiers;
        this.repr = repr;
    }
}
