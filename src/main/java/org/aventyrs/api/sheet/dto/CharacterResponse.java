package org.aventyrs.api.sheet.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aventyrs.api.item.dto.InventoryItemDto;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.Alignment;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.character.Character.Sexo;
import org.aventyrs.core.character.CharacterStatus;
import org.aventyrs.core.character.Deity;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.character.SizeCategory;
import org.aventyrs.core.skill.SkillType;

public record CharacterResponse(
        String id,
        String name,
        RaceResponse race,
        Sexo sexo,
        Deity deity,
        Alignment alignment,
        SizeCategory sizeCategory,
        ActionProfile actionProfile,
        Map<AttributeDomain, AttributeValueResponse> attributes,
        Map<EgoDomain, EgoValueResponse> egos,
        Map<SkillType, CharacterSkillResponse> skills,
        List<String> attributeAbilities,
        Map<EgoDomain, String> egoAdvantages,
        List<String> activeAbilities,
        int actionPoints,
        int temporaryActionPointsBonus,
        CharacterStatus status,
        int reactions,
        int freeActions,
        int manaMultiplier,
        int lifeMultiplier,
        int determinationMultiplier,
        boolean centelhaSuperiorSelected,
        List<FeatResponse> feats,
        List<InventoryItemDto> equipment,
        TitleResponse primaryTitle,
        TitleResponse secondaryTitle,
        TitleResponse tertiaryTitle,
        List<String> spells,
        List<MimetizedSpellResponse> mimetizedSpells,
        // Aprendizado Rápido's two Perícias (core Character#getQuickLearningSkills) and the Centelhas
        // the character still has (Character#getCentelhas) — null reads as "none recorded" / 3.
        Set<SkillType> quickLearningSkills,
        Integer centelhas,
        // The two Antecedentes (core Character#getBackgrounds) — empty when none chosen.
        List<BackgroundResponse> backgrounds,
        // Defeitos (in force and overcome) and Qualidades — empty when none.
        List<DefectResponse> defects,
        List<QualityResponse> qualities,
        org.aventyrs.core.character.DevotionTier devotionTier,
        List<org.aventyrs.api.sheet.dto.DevotionPickDto> devotionPicks,
        // Aliado da Natureza's trained creatures (core Character#getTrainedCompanions) — empty when none.
        List<org.aventyrs.api.sheet.dto.TrainedCompanionDto> trainedCompanions
) {
    /** The shape before core 0.0.103 added Aliado da Natureza's trained creatures — none. */
    public CharacterResponse(String id, String name, RaceResponse race, Sexo sexo, Deity deity, Alignment alignment, SizeCategory sizeCategory, ActionProfile actionProfile, Map<AttributeDomain, AttributeValueResponse> attributes, Map<EgoDomain, EgoValueResponse> egos, Map<SkillType, CharacterSkillResponse> skills, List<String> attributeAbilities, Map<EgoDomain, String> egoAdvantages, List<String> activeAbilities, int actionPoints, int temporaryActionPointsBonus, CharacterStatus status, int reactions, int freeActions, int manaMultiplier, int lifeMultiplier, int determinationMultiplier, boolean centelhaSuperiorSelected, List<FeatResponse> feats, List<InventoryItemDto> equipment, TitleResponse primaryTitle, TitleResponse secondaryTitle, TitleResponse tertiaryTitle, List<String> spells, List<MimetizedSpellResponse> mimetizedSpells, Set<SkillType> quickLearningSkills, Integer centelhas, List<BackgroundResponse> backgrounds, List<DefectResponse> defects, List<QualityResponse> qualities, org.aventyrs.core.character.DevotionTier devotionTier, List<org.aventyrs.api.sheet.dto.DevotionPickDto> devotionPicks) {
        this(id, name, race, sexo, deity, alignment, sizeCategory, actionProfile, attributes, egos, skills, attributeAbilities, egoAdvantages, activeAbilities, actionPoints, temporaryActionPointsBonus, status, reactions, freeActions, manaMultiplier, lifeMultiplier, determinationMultiplier, centelhaSuperiorSelected, feats, equipment, primaryTitle, secondaryTitle, tertiaryTitle, spells, mimetizedSpells, quickLearningSkills, centelhas, backgrounds, defects, qualities, devotionTier, devotionPicks, null);
    }

    /** The shape before core 0.0.86 added the devotion tier and its rung picks — none set. */
    public CharacterResponse(String id, String name, RaceResponse race, Sexo sexo, Deity deity, Alignment alignment, SizeCategory sizeCategory, ActionProfile actionProfile, Map<AttributeDomain, AttributeValueResponse> attributes, Map<EgoDomain, EgoValueResponse> egos, Map<SkillType, CharacterSkillResponse> skills, List<String> attributeAbilities, Map<EgoDomain, String> egoAdvantages, List<String> activeAbilities, int actionPoints, int temporaryActionPointsBonus, CharacterStatus status, int reactions, int freeActions, int manaMultiplier, int lifeMultiplier, int determinationMultiplier, boolean centelhaSuperiorSelected, List<FeatResponse> feats, List<InventoryItemDto> equipment, TitleResponse primaryTitle, TitleResponse secondaryTitle, TitleResponse tertiaryTitle, List<String> spells, List<MimetizedSpellResponse> mimetizedSpells, Set<SkillType> quickLearningSkills, Integer centelhas, List<BackgroundResponse> backgrounds, List<DefectResponse> defects, List<QualityResponse> qualities) {
        this(id, name, race, sexo, deity, alignment, sizeCategory, actionProfile, attributes, egos, skills, attributeAbilities, egoAdvantages, activeAbilities, actionPoints, temporaryActionPointsBonus, status, reactions, freeActions, manaMultiplier, lifeMultiplier, determinationMultiplier, centelhaSuperiorSelected, feats, equipment, primaryTitle, secondaryTitle, tertiaryTitle, spells, mimetizedSpells, quickLearningSkills, centelhas, backgrounds, defects, qualities, null, null);
    }

    /** The shape before core 0.0.72 added the Defeitos e Qualidades. */
    public CharacterResponse(String id,
            String name,
            RaceResponse race,
            Sexo sexo,
            Deity deity,
            Alignment alignment,
            SizeCategory sizeCategory,
            ActionProfile actionProfile,
            Map<AttributeDomain, AttributeValueResponse> attributes,
            Map<EgoDomain, EgoValueResponse> egos,
            Map<SkillType, CharacterSkillResponse> skills,
            List<String> attributeAbilities,
            Map<EgoDomain, String> egoAdvantages,
            List<String> activeAbilities,
            int actionPoints,
            int temporaryActionPointsBonus,
            CharacterStatus status,
            int reactions,
            int freeActions,
            int manaMultiplier,
            int lifeMultiplier,
            int determinationMultiplier,
            boolean centelhaSuperiorSelected,
            List<FeatResponse> feats,
            List<InventoryItemDto> equipment,
            TitleResponse primaryTitle,
            TitleResponse secondaryTitle,
            TitleResponse tertiaryTitle,
            List<String> spells,
            List<MimetizedSpellResponse> mimetizedSpells,
            Set<SkillType> quickLearningSkills,
            Integer centelhas,
            List<BackgroundResponse> backgrounds) {
        this(id, name, race, sexo, deity, alignment, sizeCategory, actionProfile, attributes, egos, skills, attributeAbilities, egoAdvantages, activeAbilities, actionPoints, temporaryActionPointsBonus, status, reactions, freeActions, manaMultiplier, lifeMultiplier, determinationMultiplier, centelhaSuperiorSelected, feats, equipment, primaryTitle, secondaryTitle, tertiaryTitle, spells, mimetizedSpells, quickLearningSkills, centelhas, backgrounds, List.of(), List.of());
    }

    /** The shape before core 0.0.71 added the Antecedentes. */
    public CharacterResponse(String id,
            String name,
            RaceResponse race,
            Sexo sexo,
            Deity deity,
            Alignment alignment,
            SizeCategory sizeCategory,
            ActionProfile actionProfile,
            Map<AttributeDomain, AttributeValueResponse> attributes,
            Map<EgoDomain, EgoValueResponse> egos,
            Map<SkillType, CharacterSkillResponse> skills,
            List<String> attributeAbilities,
            Map<EgoDomain, String> egoAdvantages,
            List<String> activeAbilities,
            int actionPoints,
            int temporaryActionPointsBonus,
            CharacterStatus status,
            int reactions,
            int freeActions,
            int manaMultiplier,
            int lifeMultiplier,
            int determinationMultiplier,
            boolean centelhaSuperiorSelected,
            List<FeatResponse> feats,
            List<InventoryItemDto> equipment,
            TitleResponse primaryTitle,
            TitleResponse secondaryTitle,
            TitleResponse tertiaryTitle,
            List<String> spells,
            List<MimetizedSpellResponse> mimetizedSpells,
            Set<SkillType> quickLearningSkills,
            Integer centelhas) {
        this(id, name, race, sexo, deity, alignment, sizeCategory, actionProfile, attributes, egos, skills, attributeAbilities, egoAdvantages, activeAbilities, actionPoints, temporaryActionPointsBonus, status, reactions, freeActions, manaMultiplier, lifeMultiplier, determinationMultiplier, centelhaSuperiorSelected, feats, equipment, primaryTitle, secondaryTitle, tertiaryTitle, spells, mimetizedSpells, quickLearningSkills, centelhas, null);
    }

    /** The shape before core 0.0.67/0.0.68 added Aprendizado Rápido and Centelhas. */
    public CharacterResponse(String id,
                             String name,
                             RaceResponse race,
                             Sexo sexo,
                             Deity deity,
                             Alignment alignment,
                             SizeCategory sizeCategory,
                             ActionProfile actionProfile,
                             Map<AttributeDomain, AttributeValueResponse> attributes,
                             Map<EgoDomain, EgoValueResponse> egos,
                             Map<SkillType, CharacterSkillResponse> skills,
                             List<String> attributeAbilities,
                             Map<EgoDomain, String> egoAdvantages,
                             List<String> activeAbilities,
                             int actionPoints,
                             int temporaryActionPointsBonus,
                             CharacterStatus status,
                             int reactions,
                             int freeActions,
                             int manaMultiplier,
                             int lifeMultiplier,
                             int determinationMultiplier,
                             boolean centelhaSuperiorSelected,
                             List<FeatResponse> feats,
                             List<InventoryItemDto> equipment,
                             TitleResponse primaryTitle,
                             TitleResponse secondaryTitle,
                             TitleResponse tertiaryTitle,
                             List<String> spells,
                             List<MimetizedSpellResponse> mimetizedSpells) {
        this(id, name, race, sexo, deity, alignment, sizeCategory, actionProfile, attributes, egos, skills,
                attributeAbilities, egoAdvantages, activeAbilities, actionPoints, temporaryActionPointsBonus,
                status, reactions, freeActions, manaMultiplier, lifeMultiplier, determinationMultiplier,
                centelhaSuperiorSelected, feats, equipment, primaryTitle, secondaryTitle, tertiaryTitle,
                spells, mimetizedSpells, null, null);
    }
}
