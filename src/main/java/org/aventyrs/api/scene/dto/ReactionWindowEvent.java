package org.aventyrs.api.scene.dto;

import java.util.List;
import java.util.Map;

/**
 * Outbound on {@code /topic/scenes/{sceneId}/reaction-windows} — a window opened, an answer came in, or it closed (core
 * 0.1.6.17). The acting client continues its action once one arrives {@code closed}.
 */
public record ReactionWindowEvent(String windowId, String kind, String actorCharacterSheetId,
                                  List<String> reactorCharacterSheetIds, GridPositionDto position, int elevation,
                                  String description, Map<String, String> answers, boolean closed) {
}
