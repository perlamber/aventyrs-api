package org.aventyrs.api.scene;

import java.util.List;

import org.aventyrs.api.scene.dto.BlessingDto;

/**
 * Persisted mirror of one Título activation in a Scene, paired with the combatant that activated
 * it — referenced by {@code characterSheetId} rather than embedding the combatant, the same
 * reasoning {@link SceneParticipantEntry} and {@link SceneActionEntry} both follow.
 *
 * <p>Core models no such record: an activation is resolved and forgotten client-side, and nothing
 * in aventyrs-core keeps a log of them. This exists purely so the Scene's own history survives a
 * reconnect, exactly as {@link SceneActionEntry} does for rolls.
 */
public record SceneAbilityEntry(
        String characterSheetId,
        String titleType,
        String abilityId,
        String abilityName,
        Integer determinationPointsSpent,
        Integer hitPointsSpent,
        Integer turnNumber,
        List<BlessingDto> blessings,
        String enchanterCharacterSheetId,
        List<String> boundCharacterSheetIds,
        Integer enchantmentRounds) {

    /**
     * Boxed, not primitive, and every read of one goes through {@link #orZero}.
     *
     * <p>A stored document is older than the code reading it. This record grew three components
     * after the first entries were already persisted, and Spring Data cannot instantiate a record
     * whose primitive component has no stored value — it refuses with "Parameter X must not be
     * null", which failed <em>every</em> scene read, not just the one carrying the old entry. The
     * same "null on any document persisted before it existed" tolerance {@code
     * SceneService#actionHistoryOf} and {@code #connectionsOf} already apply, moved into the shape
     * itself so the next added field cannot repeat it.
     */
    public static int orZero(final Integer value) {
        return value == null ? 0 : value;
    }
}
