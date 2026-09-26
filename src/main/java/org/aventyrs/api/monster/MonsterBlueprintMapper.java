package org.aventyrs.api.monster;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aventyrs.api.monster.MonsterBlueprintEntry.MonsterAdjustmentsEntry;
import org.aventyrs.api.monster.MonsterBlueprintEntry.MonstrousAbilityEntry;
import org.aventyrs.api.monster.dto.MonsterAdjustmentsDto;
import org.aventyrs.api.monster.dto.MonsterBlueprintDto;
import org.aventyrs.api.monster.dto.MonstrousAbilityDto;
import org.aventyrs.api.sheet.CharacterEntry;
import org.aventyrs.api.sheet.CombatantSheetMapper;
import org.aventyrs.api.sheet.FeatEntry;
import org.aventyrs.api.sheet.dto.AttributeValueDto;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.CharacterSkillDto;
import org.aventyrs.api.sheet.dto.EgoValueDto;
import org.aventyrs.api.sheet.dto.FeatDto;
import org.aventyrs.api.sheet.dto.RaceDto;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.character.AttributeValue;
import org.aventyrs.core.character.Character;
import org.aventyrs.core.character.CharacterSkill;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.character.SizeCategory;
import org.aventyrs.core.feat.Feat;
import org.aventyrs.core.feat.FeatCatalog;
import org.aventyrs.core.monster.MonsterAdjustments;
import org.aventyrs.core.monster.MonsterBlueprint;
import org.aventyrs.core.monster.MonsterKind;
import org.aventyrs.core.monster.MonstrousAbilitySelection;
import org.aventyrs.core.monster.model.MonstrousAbility;
import org.aventyrs.core.skill.SkillSpecialization;
import org.aventyrs.core.skill.SkillType;

/**
 * The one place a monster's wire/persisted blueprint meets core's {@link MonsterBlueprint}.
 *
 * <p>Unlike the character-sheet mappers — which pass Talento and ability names through untouched
 * and never build a core object — this one must: every number a monster presents is derived by
 * core, so the API has to hand core a real blueprint. Names are resolved here, and an unknown one
 * is a {@code 400} ({@link IllegalArgumentException}), not a silently dropped entry.
 *
 * <p>A Talento resolves to its <b>catalog</b> constant; a choice it carries ({@link FeatEntry#choices()})
 * is stored and returned as given but not applied to the core copy. The blueprint only needs a
 * Talento's category (for validation) and none of the choice-carrying Talentos moves a number the
 * monster presents.
 */
public final class MonsterBlueprintMapper {

    private MonsterBlueprintMapper() {
    }

    // ---- DTO ↔ entry ---------------------------------------------------------------------------

    public static MonsterBlueprintEntry toEntry(MonsterBlueprintDto dto) {
        return new MonsterBlueprintEntry(
                dto.name(),
                dto.powerDegree(),
                dto.kind() == null ? MonsterKind.REGULAR : dto.kind(),
                dto.sizeCategory() == null ? SizeCategory.ZERO : dto.sizeCategory(),
                orEmpty(dto.attributeBases()),
                orEmpty(dto.trainedSkills()),
                orEmpty(dto.gnoseUpgrades()),
                orEmpty(dto.progressionUpgrades()),
                dto.models() == null ? List.of() : List.copyOf(dto.models()),
                dto.abilities() == null ? List.of() : dto.abilities().stream()
                        .map(a -> new MonstrousAbilityEntry(a.model(), a.ability(), a.choice()))
                        .toList(),
                dto.feats() == null ? List.of() : dto.feats().stream().map(MonsterBlueprintMapper::toFeatEntry).toList(),
                orEmpty(dto.egoAllocation()),
                toAdjustmentsEntry(dto.adjustments()),
                dto.undead() != null && dto.undead(),
                orEmpty(dto.criticalEffectImmunities()),
                dto.famaPositiva(),
                dto.famaNegativa());
    }

    public static MonsterBlueprintDto toDto(MonsterBlueprintEntry entry) {
        MonsterAdjustmentsEntry adjustments = adjustmentsOf(entry);
        return new MonsterBlueprintDto(
                entry.name(),
                entry.powerDegree(),
                entry.kind(),
                entry.sizeCategory(),
                orEmpty(entry.attributeBases()),
                orEmpty(entry.trainedSkills()),
                orEmpty(entry.gnoseUpgrades()),
                orEmpty(entry.progressionUpgrades()),
                entry.models() == null ? List.of() : entry.models(),
                entry.abilities() == null ? List.of() : entry.abilities().stream()
                        .map(a -> new MonstrousAbilityDto(a.model(), a.ability(), a.choice()))
                        .toList(),
                entry.feats() == null ? List.of() : entry.feats().stream().map(MonsterBlueprintMapper::toFeatDto).toList(),
                orEmpty(entry.egoAllocation()),
                new MonsterAdjustmentsDto(
                        orEmpty(adjustments.skillLevelShifts()),
                        orEmpty(adjustments.skillBonuses()),
                        adjustments.physicalDefense(),
                        adjustments.magicDefense(),
                        adjustments.actionPoints(),
                        adjustments.lifeMultiplier(),
                        adjustments.manaMultiplier(),
                        adjustments.determinationMultiplier()),
                entry.undead(),
                orEmpty(entry.criticalEffectImmunities()),
                entry.famaPositiva(),
                entry.famaNegativa());
    }

    // ---- entry → core --------------------------------------------------------------------------

    /**
     * The core blueprint this entry describes.
     *
     * @throws IllegalArgumentException naming a Habilidade or Talento core doesn't know
     */
    public static MonsterBlueprint toCore(MonsterBlueprintEntry entry) {
        MonsterAdjustmentsEntry adjustments = adjustmentsOf(entry);
        MonsterBlueprint.MonsterBlueprintBuilder builder = MonsterBlueprint.builder()
                .name(entry.name() == null ? "" : entry.name())
                .powerDegree(entry.powerDegree())
                .kind(entry.kind() == null ? MonsterKind.REGULAR : entry.kind())
                .sizeCategory(entry.sizeCategory() == null ? SizeCategory.ZERO : entry.sizeCategory())
                .attributeBases(orEmpty(entry.attributeBases()))
                .trainedSkills(orEmpty(entry.trainedSkills()))
                .gnoseUpgrades(orEmpty(entry.gnoseUpgrades()))
                .progressionUpgrades(orEmpty(entry.progressionUpgrades()))
                .models(entry.models() == null ? List.of() : entry.models())
                .egoAllocation(orEmpty(entry.egoAllocation()))
                .undead(entry.undead())
                .criticalEffectImmunities(orEmpty(entry.criticalEffectImmunities()))
                .famaPositiva(entry.famaPositiva())
                .famaNegativa(entry.famaNegativa())
                .adjustments(MonsterAdjustments.builder()
                        .skillLevelShifts(orEmpty(adjustments.skillLevelShifts()))
                        .skillBonuses(orEmpty(adjustments.skillBonuses()))
                        .physicalDefense(adjustments.physicalDefense())
                        .magicDefense(adjustments.magicDefense())
                        .actionPoints(adjustments.actionPoints())
                        .lifeMultiplier(adjustments.lifeMultiplier())
                        .manaMultiplier(adjustments.manaMultiplier())
                        .determinationMultiplier(adjustments.determinationMultiplier())
                        .build());
        if (entry.abilities() != null) {
            for (MonstrousAbilityEntry ability : entry.abilities()) {
                builder.ability(MonstrousAbilitySelection.of(resolveAbility(ability), ability.choice()));
            }
        }
        if (entry.feats() != null) {
            for (FeatEntry feat : entry.feats()) {
                builder.feat(resolveFeat(feat.type()));
            }
        }
        return builder.build();
    }

    private static MonstrousAbility resolveAbility(MonstrousAbilityEntry entry) {
        if (entry.model() == null) {
            throw new IllegalArgumentException("Habilidade Monstruosa without a Modelo: " + entry.ability());
        }
        return entry.model().findAbility(entry.ability())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown Habilidade Monstruosa " + entry.ability() + " for Modelo " + entry.model()));
    }

    private static Feat resolveFeat(String type) {
        for (Feat feat : FeatCatalog.all()) {
            if (feat instanceof Enum<?> constant && constant.name().equals(type)) {
                return feat;
            }
        }
        throw new IllegalArgumentException("Unknown Talento: " + type);
    }

    // ---- core Character → CharacterEntry -------------------------------------------------------

    /**
     * The persisted {@link CharacterEntry} for a monster core built — so every existing reader of a
     * monster's {@code character} (name, size, attributes, egos, PA and multipliers for the Scene,
     * the Campanha's bag) keeps working. Derived, never edited: it is rewritten from the blueprint
     * on every create and update. Its Habilidades Monstruosas are <b>not</b> in {@code
     * attributeAbilities} — they aren't named constants a reader could resolve — but travel on the
     * blueprint beside it.
     */
    public static CharacterEntry toCharacterEntry(String characterId, Character character, MonsterBlueprintEntry blueprint) {
        Map<AttributeDomain, AttributeValueDto> attributes = new EnumMap<>(AttributeDomain.class);
        for (AttributeDomain domain : AttributeDomain.values()) {
            AttributeValue value = character.getAttributes().getAttribute(domain);
            attributes.put(domain, new AttributeValueDto(value.getBase(), value.getRacialBonus(), value.getVariable()));
        }
        Map<EgoDomain, EgoValueDto> egos = new EnumMap<>(EgoDomain.class);
        for (EgoDomain domain : EgoDomain.values()) {
            egos.put(domain, new EgoValueDto(character.getEgos().getEgo(domain).getBase(),
                    character.getEgos().getEgo(domain).getVariable()));
        }
        Map<SkillType, CharacterSkillDto> skills = new EnumMap<>(SkillType.class);
        for (Map.Entry<SkillType, CharacterSkill> skill : character.getSkills().entrySet()) {
            List<String> specializations = new ArrayList<>();
            for (SkillSpecialization specialization : skill.getValue().getSpecializations()) {
                specializations.add(((Enum<?>) specialization).name());
            }
            skills.put(skill.getKey(), new CharacterSkillDto(specializations, List.of(),
                    skill.getValue().getGraduation().getGraduationValue()));
        }
        CharacterDto dto = new CharacterDto(
                character.getName(),
                new RaceDto("MONSTRUOSO", null, null, null, List.of(), List.of()),
                null,
                null,
                null,
                character.getSizeCategory(),
                character.getActionProfile(),
                attributes,
                egos,
                skills,
                List.of(),
                Map.of(),
                List.of(),
                character.getActionPoints(),
                0,
                null,
                null,
                null,
                character.getManaMultiplier(),
                character.getLifeMultiplier(),
                character.getDeterminationMultiplier(),
                false,
                blueprint.feats() == null ? List.of() : blueprint.feats().stream().map(MonsterBlueprintMapper::toFeatDto).toList(),
                List.of(),
                null,
                null,
                null,
                List.of(),
                List.of());
        return CombatantSheetMapper.toEntry(characterId, dto);
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static MonsterAdjustmentsEntry toAdjustmentsEntry(MonsterAdjustmentsDto dto) {
        if (dto == null) {
            return new MonsterAdjustmentsEntry(Map.of(), Map.of(), 0, 0, 0, 0, 0, 0);
        }
        return new MonsterAdjustmentsEntry(orEmpty(dto.skillLevelShifts()), orEmpty(dto.skillBonuses()),
                dto.physicalDefense(), dto.magicDefense(), dto.actionPoints(), dto.lifeMultiplier(),
                dto.manaMultiplier(), dto.determinationMultiplier());
    }

    private static MonsterAdjustmentsEntry adjustmentsOf(MonsterBlueprintEntry entry) {
        return entry.adjustments() == null
                ? new MonsterAdjustmentsEntry(Map.of(), Map.of(), 0, 0, 0, 0, 0, 0)
                : entry.adjustments();
    }

    private static FeatEntry toFeatEntry(FeatDto dto) {
        return new FeatEntry(dto.type(), dto.choices() == null ? List.of() : dto.choices(),
                dto.chosenFeat() == null ? null : toFeatEntry(dto.chosenFeat()));
    }

    private static FeatDto toFeatDto(FeatEntry entry) {
        return new FeatDto(entry.type(), entry.choices() == null ? List.of() : entry.choices(),
                entry.chosenFeat() == null ? null : toFeatDto(entry.chosenFeat()));
    }

    private static <K, V> Map<K, V> orEmpty(Map<K, V> map) {
        return map == null ? Map.of() : map;
    }

    private static <T> Set<T> orEmpty(Set<T> set) {
        return set == null ? Set.of() : set;
    }
}
