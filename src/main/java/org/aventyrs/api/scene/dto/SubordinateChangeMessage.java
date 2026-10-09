package org.aventyrs.api.scene.dto;

/**
 * Inbound STOMP payload for {@code /app/scenes/{sceneId}/subordinates} — the GM granting a Subordinado to a participant,
 * or dismissing one (core 0.1.5.6). Relayed as is on {@code /topic/scenes/{sceneId}/subordinates}; the client that owns
 * the target applies it through core's {@code SubordinateService#grant}/{@code #dismiss} and saves the result with its
 * status frame.
 *
 * @param targetCharacterSheetId whose Subordinados change — a character or a monster
 * @param op                     {@code GRANT} or {@code DISMISS}
 * @param benefit                the {@code SubordinateBenefit} name granted; {@code null} for a dismissal
 * @param prodigious             whether the granted one is Prodigioso
 * @param subordinateId          the id of the one dismissed; {@code null} for a grant
 */
public record SubordinateChangeMessage(String targetCharacterSheetId, Op op, String benefit, boolean prodigious,
                                       String subordinateId) {

    /** Granting one, or dismissing one. */
    public enum Op { GRANT, DISMISS }
}
