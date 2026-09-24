package org.aventyrs.api.scene.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Adds a single participant to an existing Scene, mirroring core's {@code
 * Scene#addParticipant(CharacterSheet, int, UUID)} — just the CharacterSheet, its rolled
 * initiative value, and the sub-group it joins. Unlike {@link SceneParticipantRequest} (used by
 * the bulk {@code PUT} that also repositions everyone), this has no {@code position}: the server
 * assigns the first free grid cell, since core itself has no notion of grid placement at all.
 *
 * <p>{@code concealment} enters the participant already Escondido — the GM placing a foe or a
 * character "furtivamente", or a player joining that way. {@code null} (or omitted) enters in plain
 * sight. The Furtividade behind it is resolved client-side; see {@code SceneConcealmentEntry}.
 */
public record AddParticipantRequest(
        @NotBlank String characterSheetId,
        int initiativeValue,
        @NotNull UUID group,
        @Valid ConcealmentDto concealment
) {

    /** Enters in plain sight — the shape every caller had before a participant could enter hidden. */
    public AddParticipantRequest(String characterSheetId, int initiativeValue, UUID group) {
        this(characterSheetId, initiativeValue, group, null);
    }
}
