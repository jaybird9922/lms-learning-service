package edu.lms.service;

import java.util.Locale;

/** The three roles from service.html (S / T / A). */
public enum Role {
    STUDENT, TEACHER, ADMIN;

    /** Case-insensitive; returns null for anything that is not one of the three roles. */
    public static Role parseOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Role.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
