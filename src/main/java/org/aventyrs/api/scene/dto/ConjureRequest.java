package org.aventyrs.api.scene.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.aventyrs.core.monster.MonsterKind;
import org.aventyrs.core.monster.generator.ConjurePreset;

/**
 * Conjures count plain monsters into a Cena around centre (core 0.1.7.5; see {@code SceneService#conjure}).
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code preset}, {@code powerDegree}, {@code kind}, {@code name}, {@code seed}: what core's
 *   {@code MonsterGenerator} takes, so a preview with the same values shows the same monster.</li>
 *   <li>{@code group}: the sub-group they join. Null opens a new one.</li>
 *   <li>{@code concealment}: entering hidden. The client computes it from the preview's Furtividade GD.</li>
 *   <li>{@code campaignId}: the Campanha the Mestre filtered by.</li>
 *   <li>{@code tokenImageUrl}: a {@code bundled:conjured/<file>.png} key from the client's token box. Null means
 *   the preset's own.</li>
 * </ul>
 */
public record ConjureRequest(
        @NotNull ConjurePreset preset,
        @Min(0) @Max(80) int powerDegree,
        MonsterKind kind,
        String name,
        @Min(1) @Max(20) int count,
        long seed,
        UUID group,
        @Valid ConcealmentDto concealment,
        String campaignId,
        @NotNull @Valid GridPositionDto centre,
        String tokenImageUrl
) {
}
