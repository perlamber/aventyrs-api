package org.aventyrs.api.scene;

import java.time.Instant;
import java.util.List;

import org.aventyrs.core.skill.DifficultyLevel;
import org.aventyrs.core.skill.SkillType;

/**
 * One roll the Narrador has asked the table for, persisted so a client that joins late — or
 * reconnects mid-request — still sees it, exactly as {@link SceneActionEntry} does for a resolved
 * action.
 *
 * <p>Referenced by {@code characterSheetId} rather than embedding combatants, same reasoning as
 * {@link SceneParticipantEntry}, and reusing core's own {@link SkillType}/{@link DifficultyLevel}
 * directly rather than restating them, same convention {@link SceneActionEntry} follows.
 *
 * @param requestId  server-generated, so responses have something stable to point at. Stamped here
 *                   rather than trusted from the client, like {@code ScenePingEvent}'s own
 *                   {@code Instant.now()}.
 * @param difficultyLevel the Grau de Dificuldade the answer is judged against. One of the eight
 *                   tiers because that is what both paths speak: a foe's {@code attackDifficulty}
 *                   already is one, and core's {@code IncomingAttack#difficultyLevel} is
 *                   {@code @NonNull DifficultyLevel} with no int escape hatch.
 * @param attackBonus a flat modifier on top of that threshold; 0 for a {@link RollRequestKind#CHECK}.
 *                   It is also the only way an attack's bar lands between two tiers.
 * @param attackerCharacterSheetId the foe doing the attacking, or {@code null} for a check.
 * @param targetCharacterSheetIds who must answer. <b>Empty means everyone</b> — the Narrador asking
 *                   the whole table for an Atenção roll is the ordinary case, not a special one.
 * @param prompt     free text the Narrador wrote, or {@code null}.
 */
public record SceneRollRequestEntry(
        String requestId,
        RollRequestKind kind,
        SkillType skill,
        DifficultyLevel difficultyLevel,
        int attackBonus,
        String attackerCharacterSheetId,
        List<String> targetCharacterSheetIds,
        String prompt,
        Instant requestedAt) {
}
