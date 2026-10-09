package org.aventyrs.api.scene.dto;

import org.aventyrs.core.character.CharacterStatus;

/**
 * Wire shape of a persisted participant combat-state change — broadcast on {@code
 * /topic/scenes/{sceneId}/status} to every client watching the scene, including the one that sent
 * the {@link CharacterStatusMessage} that produced it (its own token badge is confirmed by the
 * echo, the same single-source-of-truth flow {@link TokenMovedEvent} uses for moves). {@code bleedingEffects} is
 * the sender's running Sangramentos, so every board's stand-in can mirror them — {@code null} when not reported.
 * {@code subordinates} likewise (core 0.1.5.6), so a Prodigioso one reaches allies on other boards.
 */
public record CharacterStatusChangedEvent(String characterSheetId, int hitPointsSpent,
                                          int magicPointsSpent, int determinationPointsSpent,
                                          CharacterStatus status,
                                          java.util.List<org.aventyrs.api.sheet.dto.BleedingDto> bleedingEffects,
                                          java.util.List<org.aventyrs.api.sheet.dto.SubordinateDto> subordinates) {

    /** Without the running Sangramentos or Subordinados — a frame that didn't report them. */
    public CharacterStatusChangedEvent(String characterSheetId, int hitPointsSpent, int magicPointsSpent,
            int determinationPointsSpent, CharacterStatus status) {
        this(characterSheetId, hitPointsSpent, magicPointsSpent, determinationPointsSpent, status, null, null);
    }
}
