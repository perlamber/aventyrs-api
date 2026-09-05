package org.aventyrs.api.scene.dto;

import java.time.Instant;
import java.util.List;

import org.aventyrs.api.scene.RollResponseKind;

/**
 * One answer to a roll request, broadcast on {@code /topic/scenes/{sceneId}/roll-responses} so the
 * whole table sees who answered and how — and so every client, including the one that rolled,
 * settles its own "already answered" state off the same broadcast rather than optimistically.
 */
public record RollRespondedEvent(
        String requestId,
        String characterSheetId,
        RollResponseKind kind,
        List<Integer> dice,
        Boolean succeeded,
        Integer margin,
        Integer total,
        Integer requiredTotal,
        String note,
        Instant respondedAt) {
}
