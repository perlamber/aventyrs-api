package org.aventyrs.api.scene.dto;

import java.util.List;

import org.aventyrs.api.scene.RollResponseKind;

/**
 * One player's answer to a roll request — inbound on {@code /app/scenes/{sceneId}/roll-responses}.
 *
 * <p>The verdict is carried, not computed: this server runs no rules engine, so the answering
 * client resolves through core and reports the result, the same trust {@link RecordActionMessage}
 * already places in a reported roll.
 *
 * <p>{@code succeeded}/{@code margin} are boxed because they are <b>tri-state</b> — {@code null}
 * means the roll stated no Grau de Dificuldade, which is not a failure.
 */
public record RollResponseMessage(
        String requestId,
        String characterSheetId,
        RollResponseKind kind,
        List<Integer> dice,
        Boolean succeeded,
        Integer margin,
        Integer total,
        Integer requiredTotal,
        String note) {
}
