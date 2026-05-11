package com.uaeitjobs.util;

import java.text.Normalizer;
import java.util.Locale;

public final class SlugGenerator {
    private SlugGenerator() {
    }

    public static String from(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "job" : normalized;
    }
}
