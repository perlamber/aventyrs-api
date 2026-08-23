package org.aventyrs.api.sheet.dto;

import java.util.List;

public record TitleResponse(
        String type,
        List<String> specializations,
        List<String> abilities
) {
}
