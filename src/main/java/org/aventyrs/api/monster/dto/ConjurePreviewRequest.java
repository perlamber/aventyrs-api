package org.aventyrs.api.monster.dto;

import jakarta.validation.constraints.NotNull;
import org.aventyrs.core.monster.MonsterKind;
import org.aventyrs.core.monster.generator.ConjurePreset;

/**
 * What core's {@code MonsterGenerator} needs for one monster (core 0.1.7.5). The same seed gives the same monster,
 * so a preview and the conjure that follows it agree. A null kind is Regular, and a blank name is the preset's own.
 */
public record ConjurePreviewRequest(
        @NotNull ConjurePreset preset,
        int powerDegree,
        MonsterKind kind,
        String name,
        long seed
) {
}
