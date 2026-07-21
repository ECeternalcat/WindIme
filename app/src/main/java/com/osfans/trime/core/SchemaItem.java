package com.osfans.trime.core;

/** Schema list entry (JNI contract: ctor {@code (String schemaId, String name)}). */
public final class SchemaItem {
    public final String schemaId;
    public final String name;

    public SchemaItem(String schemaId, String name) {
        this.schemaId = schemaId;
        this.name = name;
    }
}
