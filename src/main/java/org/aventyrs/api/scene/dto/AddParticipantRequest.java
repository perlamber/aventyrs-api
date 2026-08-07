package org.aventyrs.api.scene.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Adds a single participant to an existing Scene, mirroring core's {@code
 * Scene#addParticipant(CharacterSheet, int, UUID)} — just the CharacterSheet, its rolled
 * initiative value, and the sub-group it joins. Unlike {@link SceneParticipantRequest} (used by
 * the bulk {@code PUT} that also repositions everyone), this has no {@code position}: the server
 * assigns the first free grid cell, since core itself has no notion of grid placement at all.
 */
public record AddParticipantRequest(
        @NotBlank String characterSheetId,
        int initiativeValue,
        @NotNull UUID group
) {
}
