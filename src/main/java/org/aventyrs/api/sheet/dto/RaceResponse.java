package org.aventyrs.api.sheet.dto;

import java.util.List;
import org.aventyrs.core.character.AttributeDomain;

public record RaceResponse(
        String type,
        String parentRaceType,
        String linhagem,
        AttributeDomain chosenInheritedAttribute,
        List<String> inheritedRacialAbilities,
        List<String> inheritedAttributeAbilities
) {
}
