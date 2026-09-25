package org.aventyrs.api.scene.dto;

/**
 * One effect a Título activation put on a single named target whose real sheet another client owns —
 * relayed so that client applies it to the real sheet (a stand-in here carries none of its state).
 * Every numeric field is boxed and nullable: this rides inside persisted ability entries, and a
 * primitive added later would make older documents unreadable.
 *
 * <p>{@code kind} is one of:
 * <ul>
 *   <li>{@code HEAL} — recover PV. {@code healing} says how much: {@code FIXED} ({@code amount}),
 *       {@code REST_CURTO}/{@code REST_LONGO} (a Descanso's worth, read off the target's own Vigor) or
 *       {@code VIGOR} (the target's Vigor). {@code healingKey} is the heal effect's key for the Coma
 *       cap; {@code healerBonus}, {@code bypassesComaCap} and {@code revivalWindowRounds} are the
 *       healer's half, resolved on the healer's client (core's {@code RelayedHealer}).</li>
 *   <li>{@code PROTECT} — Cura Protetora's +4 Defesas for 1 Rodada.</li>
 *   <li>{@code POINTS} — recover up to {@code amount} of {@code resource} (a {@code ResourceType}).</li>
 *   <li>{@code EGO_LOAN} — 1 temporary point of {@code egoDomain} lent until the end of the Cena.</li>
 * </ul>
 */
public record TargetEffectDto(String targetCharacterSheetId, String kind, Integer amount, String healing,
                              String healingKey, Integer healerBonus, Boolean bypassesComaCap,
                              Integer revivalWindowRounds, String resource, String egoDomain) {
}
