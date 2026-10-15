package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * Inbound STOMP payload for {@code /app/scenes/{sceneId}/aura} — what the GM's client, the only one holding real foe
 * sheets, works out for the monster auras of core 0.1.7.4 (manual 20261015) and the owners of the sheets they reach
 * must apply. GM-only ({@code StompAuthChannelInterceptor}), relayed as is on {@code /topic/scenes/{sceneId}/aura} and
 * never persisted: an owner applies it to its core sheet and saves the result through its next status frame.
 *
 * <ul>
 *   <li>{@link Kind#STATE} — the full set of aura grants on {@code targetCharacterSheetId} right now; the owner
 *   replaces what auras had granted it, keeping a dropped grant's {@code lingerRounds} remainder.</li>
 *   <li>{@link Kind#HIT} — one target's share of an aura's pulse, from {@code holderCharacterSheetId}.</li>
 *   <li>{@link Kind#FOOTPRINTS} — every aura on the board, so each table draws it and treats a Terreno Difícil one as
 *   such in its movement map.</li>
 * </ul>
 */
public record AuraMessage(Kind kind, String holderCharacterSheetId, String targetCharacterSheetId,
                          List<AuraGrantDto> grants, AuraHitDto hit, List<AuraFootprintDto> footprints) {

    public enum Kind {
        STATE,
        HIT,
        FOOTPRINTS
    }

    /**
     * One open-ended grant: a figure ({@code modifierType} + {@code value}) or a ward against Condições, from the aura
     * named {@code source}.
     */
    public record AuraGrantDto(String source, String modifierType, Integer value, List<String> wardedConditions,
                               Integer lingerRounds) {
    }

    /** One target's share of a pulse — damage before the target's own mitigation, a heal, Condições, Autocontrole. */
    public record AuraHitDto(int damage, String damageType, String element, String sanctity, int heal,
                             List<String> conditions, int conditionRounds, int autocontroleLoss,
                             boolean possessIfEmptied) {
    }

    /** An aura on the board: who emits it, how far it reaches in UD, and whether its area is Terreno Difícil. */
    public record AuraFootprintDto(String holderCharacterSheetId, String source, int radiusUd,
                                   boolean difficultTerrain) {
    }

    /** Whether the message is complete for its kind. */
    public boolean isWellFormed() {
        if (kind == null) {
            return false;
        }
        return switch (kind) {
            case STATE -> targetCharacterSheetId != null && grants != null;
            case HIT -> targetCharacterSheetId != null && holderCharacterSheetId != null && hit != null;
            case FOOTPRINTS -> footprints != null;
        };
    }
}
