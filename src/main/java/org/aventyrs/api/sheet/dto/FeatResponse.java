package org.aventyrs.api.sheet.dto;

import java.util.List;

public record FeatResponse(
        String type,
        List<String> choices,
        FeatResponse chosenFeat
) {
}
