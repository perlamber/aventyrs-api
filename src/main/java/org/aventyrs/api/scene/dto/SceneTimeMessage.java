package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * The GM passed in-game time — "Passar tempo" — or granted a Descanso, sent to {@code
 * /app/scenes/{sceneId}/time} (or {@code POST /scenes/{id}/time}). A Descanso the GM grants is a
 * <b>Descanso Verdadeiro</b>.
 *
 * @param hours              in-game hours that pass (a Descanso's own length included)
 * @param restType           {@code RestType} name of the Descanso, or {@code null} for time alone
 * @param characterSheetIds  who rests; empty or {@code null} means every participant
 */
public record SceneTimeMessage(int hours, String restType, List<String> characterSheetIds) {
}
