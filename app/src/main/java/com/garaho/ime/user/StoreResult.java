package com.garaho.ime.user;

/**
 * Outcome of a mutating user-store operation (improvement doc §5). Lets the UI
 * report validation failures (empty / too long / duplicate) instead of failing
 * silently, and signal I/O trouble.
 */
public enum StoreResult {
    OK,
    EMPTY,
    TOO_LONG,
    TOO_MANY,
    DUPLICATE,
    IO_ERROR;

    public boolean ok() {
        return this == OK;
    }
}
