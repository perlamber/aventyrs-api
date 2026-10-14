package org.aventyrs.api.scene.dto;

/**
 * Inbound STOMP payload for {@code /app/scenes/{sceneId}/hazards} — the GM subjecting a participant to a fall or to the
 * lack of air (core 0.1.6.14, {@code FallService}/{@code AsphyxiaService}). Relayed as is on {@code
 * /topic/scenes/{sceneId}/hazards}; the client that owns the target applies it to its core sheet and saves the result
 * through its next status frame.
 *
 * @param targetCharacterSheetId who it happens to
 * @param kind                   what happens
 * @param heightUd               the height fallen, for {@link Kind#FALL} only
 */
public record HazardMessage(String targetCharacterSheetId, Kind kind, Integer heightUd) {

    /** What the GM did to the target. */
    public enum Kind {
        /** A fall of {@code heightUd}. */
        FALL,
        /** The target stops breathing — submerged, or in a gas. */
        STOP_BREATHING,
        /** The target breathes again. */
        BREATHE,
        /** One Rodada passes without air, outside combat (in combat the owner ticks it at the Turn start). */
        ASPHYXIA_ROUND
    }

    /** Whether the message is complete — a fall names a height that is not negative. */
    public boolean isWellFormed() {
        return targetCharacterSheetId != null && kind != null
                && (kind != Kind.FALL || heightUd != null && heightUd >= 0);
    }
}
