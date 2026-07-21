package com.osfans.trime.core;

/**
 * Engine status (JNI contract:
 * ctor {@code (String schemaId, String schemaName, boolean isDisabled,
 * boolean isComposing, boolean isAsciiMode, boolean isFullShape,
 * boolean isSimplified, boolean isTraditional, boolean isAsciiPunct)}).
 */
public final class StatusProto {
    public final String schemaId;
    public final String schemaName;
    public final boolean isDisabled;
    public final boolean isComposing;
    public final boolean isAsciiMode;
    public final boolean isFullShape;
    public final boolean isSimplified;
    public final boolean isTraditional;
    public final boolean isAsciiPunct;

    public StatusProto(String schemaId, String schemaName, boolean isDisabled,
                       boolean isComposing, boolean isAsciiMode, boolean isFullShape,
                       boolean isSimplified, boolean isTraditional, boolean isAsciiPunct) {
        this.schemaId = schemaId;
        this.schemaName = schemaName;
        this.isDisabled = isDisabled;
        this.isComposing = isComposing;
        this.isAsciiMode = isAsciiMode;
        this.isFullShape = isFullShape;
        this.isSimplified = isSimplified;
        this.isTraditional = isTraditional;
        this.isAsciiPunct = isAsciiPunct;
    }
}
