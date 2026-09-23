package org.aventyrs.api.sheet.dto;

import java.util.List;
import java.util.Map;

public record TitleResponse(
        String type,
        List<String> specializations,
        List<String> abilities,
        Map<String, String> choices
) {

    /** A Título with no acquisition-time choices. */
    public TitleResponse(final String type, final List<String> specializations, final List<String> abilities) {
        this(type, specializations, abilities, null);
    }
}
