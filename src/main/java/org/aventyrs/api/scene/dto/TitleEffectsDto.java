package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * What a Título activation did to <em>other</em> characters beyond Blessings and bindings — damage,
 * Condições, an inspired Frenesi — each of which only the client owning the affected sheet can apply.
 * Every part is optional; {@code null} on an activation that did none of it.
 */
public record TitleEffectsDto(List<AreaDamageDto> areaDamage, List<InflictedConditionDto> conditions,
                              InspiredFrenzyDto inspiredFrenzy) {
}
