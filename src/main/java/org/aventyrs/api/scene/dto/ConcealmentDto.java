package org.aventyrs.api.scene.dto;

import jakarta.validation.constraints.NotNull;
import org.aventyrs.core.skill.DifficultyLevel;

/**
 * Wire shape of a participant's concealment — see {@code org.aventyrs.api.scene.SceneConcealmentEntry}
 * for what each field is and who computes it. {@code null} wherever a participant is not hidden.
 */
public record ConcealmentDto(
        @NotNull DifficultyLevel difficultyLevel,
        int bonus,
        int ordinaryConcealmentValue,
        int expertConcealmentValue) {
}
