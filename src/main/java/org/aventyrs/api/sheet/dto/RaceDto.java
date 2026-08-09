package org.aventyrs.api.sheet.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.aventyrs.core.character.AttributeDomain;

/**
 * Request-side mirror of {@link org.aventyrs.api.sheet.RaceEntry} — see its javadoc for what
 * each field means. Only {@code type} is required; {@code parentRaceType} is additionally
 * required for the 8 stateful race types (the 7 {@code AbstractMesticoRace} subclasses and
 * {@code MEIO_ELFO}), enforced in {@code CharacterSheetService#toRaceEntry} since Bean Validation
 * can't express a rule conditional on another field's free-text value.
 */
public record RaceDto(
        @NotBlank String type,
        String parentRaceType,
        String linhagem,
        AttributeDomain chosenInheritedAttribute,
        List<String> inheritedRacialAbilities,
        List<String> inheritedAttributeAbilities
) {
}
