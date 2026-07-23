package com.garaho.ime.keymap;

/** Stable identifiers and filenames for the factory map and four user maps. */
public final class KeymapSlots {

    public static final int FACTORY = 0;
    public static final int USER_MIN = 1;
    public static final int USER_MAX = 4;

    private KeymapSlots() {
    }

    public static boolean isValid(int slot) {
        return slot >= FACTORY && slot <= USER_MAX;
    }

    public static boolean isUser(int slot) {
        return slot >= USER_MIN && slot <= USER_MAX;
    }

    public static String fileName(int slot) {
        if (!isUser(slot)) {
            throw new IllegalArgumentException("Not a user keymap slot: " + slot);
        }
        return "user_keymap_" + slot + ".json";
    }
}
