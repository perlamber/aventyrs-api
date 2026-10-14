package org.aventyrs.api.scene;

import java.util.ArrayList;
import java.util.List;

/**
 * Something that invokes creatures for its caster at each Rodada boundary while it lasts — Totem de Gaea (client
 * 0.0.95), the server-side mirror of core's {@code scene.SummonSpawner}. Each creature is a {@code NatureSummon} of
 * {@code kind} at {@code conjuradorManaGraduation}, lasting {@code summonRounds}, carrying {@code enhancement} — what
 * its caster's Títulos give each one (Bruxo, core 0.1.3), {@code null} for none.
 *
 * <p>Since core 0.1.6.26 a {@code rule} may replace the single creature ({@code null} keeps it):
 * <ul>
 *   <li>{@link #FESTIM} / {@link #FESTIM_HORDA} — Festim dos Mortos' sarcophagus: the server rolls 1d6 ({@code
 *   lastFace}) and core's {@code FestimDosMortos#spawnsFor} says what rises, past {@code summonLimit} (its caster's Foco)
 *   uncontrolled; every creature acts from the next Rodada and leaves with it;</li>
 *   <li>{@link #CUSPIDOR} — Cuspidor de Pragas: two Deviante Carrascos on odd Rodadas, no Duração of its own, ending
 *   with the combat or its caster, taking them along.</li>
 * </ul>
 * {@code spawned} lists the creatures it brought; {@code hostileGroup} is the sub-group its uncontrolled ones stand in.
 */
public record SceneSummonSpawnerEntry(String casterCharacterSheetId, int remainingRounds, String kind,
                                      int conjuradorManaGraduation, List<String> powers, Integer summonRounds,
                                      org.aventyrs.core.magic.invocation.SummonEnhancement enhancement,
                                      String rule, Integer summonLimit, String hostileGroup, List<String> spawned,
                                      Integer lastFace) {

    public static final String FESTIM = "FESTIM";
    public static final String FESTIM_HORDA = "FESTIM_HORDA";
    public static final String CUSPIDOR = "CUSPIDOR";

    /** A spawner stored before core 0.1.3 — its creatures carry no enhancement. */
    public SceneSummonSpawnerEntry(String casterCharacterSheetId, int remainingRounds, String kind,
                                   int conjuradorManaGraduation, List<String> powers, Integer summonRounds) {
        this(casterCharacterSheetId, remainingRounds, kind, conjuradorManaGraduation, powers, summonRounds, null);
    }

    /** A single-creature spawner (Totem de Gaea) — the shape before core 0.1.6.26. */
    public SceneSummonSpawnerEntry(String casterCharacterSheetId, int remainingRounds, String kind,
                                   int conjuradorManaGraduation, List<String> powers, Integer summonRounds,
                                   org.aventyrs.core.magic.invocation.SummonEnhancement enhancement) {
        this(casterCharacterSheetId, remainingRounds, kind, conjuradorManaGraduation, powers, summonRounds,
                enhancement, null, null, null, List.of(), null);
    }

    /** Whether it is Festim dos Mortos' sarcophagus, either form. */
    boolean isFestim() {
        return FESTIM.equals(rule) || FESTIM_HORDA.equals(rule);
    }

    /** Whether it lasts until the combat ends rather than counting down (Cuspidor de Pragas). */
    boolean isCombatScoped() {
        return CUSPIDOR.equals(rule);
    }

    /** Whether its creatures go with it when it ends. */
    boolean takesItsSummons() {
        return isFestim() || isCombatScoped();
    }

    List<String> spawnedOrEmpty() {
        return spawned == null ? List.of() : spawned;
    }

    SceneSummonSpawnerEntry ticked() {
        return new SceneSummonSpawnerEntry(casterCharacterSheetId, isCombatScoped() ? remainingRounds : remainingRounds - 1,
                kind, conjuradorManaGraduation, powers, summonRounds, enhancement, rule, summonLimit, hostileGroup,
                spawned, lastFace);
    }

    /** Whether it has run out. A combat-scoped one never does on its own. */
    boolean isSpent() {
        return !isCombatScoped() && remainingRounds <= 0;
    }

    /** This spawner having brought ids at a roll of face ({@code null} when nothing was rolled). */
    SceneSummonSpawnerEntry withSpawned(final List<String> ids, final Integer face) {
        List<String> all = new ArrayList<>(spawnedOrEmpty());
        all.addAll(ids);
        return new SceneSummonSpawnerEntry(casterCharacterSheetId, remainingRounds, kind, conjuradorManaGraduation,
                powers, summonRounds, enhancement, rule, summonLimit, hostileGroup, List.copyOf(all),
                face == null ? lastFace : face);
    }
}
