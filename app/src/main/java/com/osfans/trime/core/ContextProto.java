package com.osfans.trime.core;

/**
 * Engine context (JNI contract:
 * ctor {@code (CompositionProto composition, MenuProto menu,
 * String input, int caretPos)}).
 */
public final class ContextProto {
    public final CompositionProto composition;
    public final MenuProto menu;
    public final String input;
    public final int caretPos;

    public ContextProto(CompositionProto composition, MenuProto menu,
                        String input, int caretPos) {
        this.composition = composition;
        this.menu = menu;
        this.input = input;
        this.caretPos = caretPos;
    }
}
