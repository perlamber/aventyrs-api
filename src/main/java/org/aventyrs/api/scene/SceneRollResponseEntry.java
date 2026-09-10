package org.aventyrs.api.scene;

import java.time.Instant;
import java.util.List;

/**
 * One player's answer to a {@link SceneRollRequestEntry}, carrying its {@code requestId} rather
 * than being nested inside the request.
 *
 * <p>Flat and separate deliberately: both lists are appended with a plain {@code $push} and no
 * positional operator, which is what makes several players answering at once safe — see {@code
 * SceneService#respondToRoll}.
 *
 * <p><b>This server does not resolve anything.</b> It runs no rules engine (see {@link
 * SceneService}'s own javadoc); the verdict here is whatever the answering client already computed
 * through core, the same trust {@link SceneActionEntry} places in a reported roll.
 *
 * @param succeeded whether the roll beat its Grau de Dificuldade — <b>tri-state</b>. {@code null}
 *                  means nobody stated what it was against, which is a third answer and never a
 *                  failure; a reader rendering it as one reports losses that never happened.
 * @param margin    signed distance from the threshold, {@code null} whenever {@code succeeded} is.
 * @param note      what a {@link RollResponseKind#REACTED} answer is declaring, or {@code null}.
 */
public record SceneRollResponseEntry(
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
