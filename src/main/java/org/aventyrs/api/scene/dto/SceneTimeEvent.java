package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * {@link SceneTimeMessage}, broadcast on {@code /topic/scenes/{sceneId}/time} so every client applies
 * it to the sheets it owns — this server holds no core sheet to apply it to.
 */
public record SceneTimeEvent(int hours, String restType, List<String> characterSheetIds) {
}
