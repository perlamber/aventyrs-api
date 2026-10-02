package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * What a Título activation did to <em>other</em> characters beyond Blessings and bindings — damage,
 * Condições, an inspired Frenesi — each of which only the client owning the affected sheet can apply.
 * Every part is optional; {@code null} on an activation that did none of it. {@code targetEffects}
 * carries what landed on one named target (a heal, a Blessing, a transfer) — see {@link TargetEffectDto}.
 */
public record TitleEffectsDto(List<AreaDamageDto> areaDamage, List<InflictedConditionDto> conditions,
                              InspiredFrenzyDto inspiredFrenzy, List<TargetEffectDto> targetEffects) {

    /** An activation with no effect on a single named target — every shape before {@code targetEffects}. */
    public TitleEffectsDto(List<AreaDamageDto> areaDamage, List<InflictedConditionDto> conditions,
                           InspiredFrenzyDto inspiredFrenzy) {
        this(areaDamage, conditions, inspiredFrenzy, null);
    }
}
