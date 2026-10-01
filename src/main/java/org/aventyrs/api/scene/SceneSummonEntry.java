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
                               Integer remainingRounds, Integer trailingRounds, boolean replaced) {

    SceneSummonEntry markedReplaced() {
        return new SceneSummonEntry(summonId, casterCharacterSheetId, exclusivityGroup, remainingRounds,
                trailingRounds, true);
    }

    SceneSummonEntry ticked() {
        return remainingRounds == null ? this : new SceneSummonEntry(summonId, casterCharacterSheetId, exclusivityGroup,
                remainingRounds - 1, trailingRounds, replaced);
    }

    SceneSummonEntry released() {
        return trailingRounds == null ? this : new SceneSummonEntry(summonId, casterCharacterSheetId, exclusivityGroup,
                trailingRounds, null, replaced);
    }

    boolean isSpent() {
        return remainingRounds != null && remainingRounds <= 0;
    }
}
