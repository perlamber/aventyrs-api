package org.aventyrs.api.scene.dto;

/**
 * A Pedra do Poder action a character took in a Cena (core 0.1.6.7) — sent to {@code /app/scenes/{sceneId}/power-stones}
 * and relayed to {@code /topic/scenes/{sceneId}/power-stones} for every client's log. Relayed only: the stone's Cargas
 * and the host's Dureza persist with the sheet, the binding and cooldowns with its {@code /status} frame.
 *
 * @param action          {@code BIND}, {@code UNBIND}, {@code LAPSE} or {@code HEAL} (Vitalitas Immanis)
 * @param stoneName       the stone ("Hematita do Vendaval (Joia)")
 * @param hostName        the host it was bound to or removed from; {@code null} for {@code HEAL}
 * @param chargesSpent    Cargas the action spent
 * @param hostDamage      Danos the host took
 * @param bearerDamage    Danos the bearer took instead (Solve-Vidas)
 * @param healed          PV restored ({@code HEAL})
 * @param actionPoints    PA paid (0 for an Ação Livre)
 */
public record PowerStoneEventMessage(String characterSheetId, String action, String stoneName, String hostName,
                                     int chargesSpent, int hostDamage, int bearerDamage, int healed,
                                     int actionPoints, int turnNumber) {
}
