package org.aventyrs.api.scene.dto;

/**
 * One hit an area effect dealt — core's {@code sheet.AreaDamage} on the wire, for the client owning
 * {@code targetCharacterSheetId} to apply to its real sheet. Type and element are Strings for the
 * reason {@link BlessingDto} gives: a client may name a constant this server's core has never heard of.
 */
public record AreaDamageDto(String targetCharacterSheetId, int amount, String damageType, String elementalType) {
}
