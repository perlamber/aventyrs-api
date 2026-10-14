package org.aventyrs.api.scene;

/**
 * An invocation standing in this Scene (client 0.0.95) — the server-side mirror of core's {@code scene.SceneSummon}.
 * {@code summonId} is both its participant id and its {@code MonsterSheetDocument}'s.
 *
 * <ul>
 *   <li>{@code remainingRounds}: its Duração; it leaves at the Rodada boundary that spends it. {@code null} while a
 *       Concentração holds it, or for one that lasts until dismissed.</li>
 *   <li>{@code trailingRounds}: the N of "Concentração + N" until the caster's focus breaks ({@link
 *       SceneService#releaseConcentration}); {@code null} once it has, or without one.</li>
 *   <li>{@code replaced}: a newer summon of its {@code exclusivityGroup} came — it leaves as its caster's Turn ends.</li>
 * </ul>
 */
public record SceneSummonEntry(String summonId, String casterCharacterSheetId, String exclusivityGroup,
                               Integer remainingRounds, Integer trailingRounds, boolean replaced,
                               boolean raisedFromCorpse, boolean uncontrolled) {

    /** A summon as stored before core 0.1.6.26 — not raised from a corpse, and controlled. */
    public SceneSummonEntry(String summonId, String casterCharacterSheetId, String exclusivityGroup,
                            Integer remainingRounds, Integer trailingRounds, boolean replaced) {
        this(summonId, casterCharacterSheetId, exclusivityGroup, remainingRounds, trailingRounds, replaced, false,
                false);
    }

    SceneSummonEntry markedReplaced() {
        return new SceneSummonEntry(summonId, casterCharacterSheetId, exclusivityGroup, remainingRounds,
                trailingRounds, true, raisedFromCorpse, uncontrolled);
    }

    SceneSummonEntry ticked() {
        return remainingRounds == null ? this : new SceneSummonEntry(summonId, casterCharacterSheetId, exclusivityGroup,
                remainingRounds - 1, trailingRounds, replaced, raisedFromCorpse, uncontrolled);
    }

    SceneSummonEntry released() {
        return trailingRounds == null ? this : new SceneSummonEntry(summonId, casterCharacterSheetId, exclusivityGroup,
                trailingRounds, null, replaced, raisedFromCorpse, uncontrolled);
    }

    /**
     * One Rodada off — an Abantesma struck (core 0.1.6.25: "quando sofrem danos a Duração da Magia é reduzida em -1
     * Rodada"), off its running Duração, or off its trailing N while still held.
     */
    SceneSummonEntry shortened() {
        if (remainingRounds != null) {
            return new SceneSummonEntry(summonId, casterCharacterSheetId, exclusivityGroup, remainingRounds - 1,
                    trailingRounds, replaced, raisedFromCorpse, uncontrolled);
        }
        return trailingRounds == null ? this : new SceneSummonEntry(summonId, casterCharacterSheetId, exclusivityGroup,
                null, Math.max(0, trailingRounds - 1), replaced, raisedFromCorpse, uncontrolled);
    }

    boolean isSpent() {
        return remainingRounds != null && remainingRounds <= 0;
    }
}
