package org.aventyrs.api.sheet;

import java.util.List;

/**
 * Persisted mirror of one Talento a character holds — a plain identifier ({@code type}, e.g.
 * {@code "ARTISTA_MARCIAL"}) plus the acquisition-time choice it carries, the same "identifier
 * plus the delta" shape {@link TitleEntry} uses for a held Título and {@link RaceEntry} for a
 * {@code Race}.
 *
 * <p>A bare {@code name()} was enough while every held Talento was a catalog enum constant, but
 * core's choice-carrying Talentos are not: each records its pick on a hand-written, <b>non-enum</b>
 * {@code AbstractFeat} subclass ({@code FocoEmPericiaFeat}, {@code EspecialistaEmArmaFeat}, …)
 * which overrides {@code Feat#catalogEntry()} so eligibility still sees it as the constant. A bare
 * name lost both halves — which constant, and what was chosen — and core refuses to grant the
 * plain constant instead ({@code FeatService#grantFeat} throws {@code FEAT_REQUIRES_CHOICE},
 * detected as {@code feat == feat.catalogEntry()}), so a document storing only names could not
 * round-trip a legal grant for the 10 constants that declare a choice.
 *
 * <p>{@code type} is always the <b>catalog</b> constant's own {@code name()} — what {@code
 * catalogEntry()} returns — never the acquired subclass's simple name, so a reader matches it
 * against the catalog the same way core's own {@code Feat#isHeldBy} does. For {@code
 * HerancaBestialFeat}, {@code ChosenSkillTraitsFeat} and {@code HabilidadeDeAtributoEscolhidaFeat}
 * — whose acquired form carries the Talento itself alongside the pick — that Talento *is* the
 * {@code type}, so nothing extra is needed to name it.
 *
 * <p>{@code choices} holds the picked values as their own enum constant names, in the order core's
 * {@code Feat#resolveRequiredChoices} declares them: one entry for a single-pick Talento ({@code
 * FocoEmPericiaFeat}'s {@code SkillType}, {@code TerrenoPrediletoFeat}'s {@code TerrainType}, the
 * {@code AttackMethod} of the three weapon-type Talentos, {@code SaqueRelampagoFeat}'s {@code
 * WeaponOrSpellChoice}), several for a multi-pick one ({@code ArmamentoDraconicoFeat}'s exactly 2
 * {@code NaturalWeapon}s, {@code MetamorfoseDraculeaFeat}'s {@code FormaMetamorfica}s). Empty, not
 * {@code null}, for a Talento taken plain.
 *
 * <p>Two shapes are carried flat here on purpose. {@code AdotadoPorSylphFeat} has <em>two</em>
 * sets (chosen {@code SkillType}s and granted {@code SkillCompetencyAbility}s, one form per
 * constructor) and they share this one list; a reader disambiguates by resolving each name against
 * its own enum, which is the same job it already does for {@link
 * CharacterSkillEntry#competencyAbilities}. {@code MetamorfoseDraculeaFeat#transformations} is not
 * carried at all — it's derived from the chosen Formas, and the resulting {@code ActiveAbility}s
 * are already persisted in {@code CharacterEntry#activeAbilities}.
 *
 * <p>{@code chosenFeat} is the one genuinely nested case: {@code ExcepcionalidadeFeat}'s pick is
 * another whole Talento ({@code DestinoFeat#EXCEPCIONALIDADE}'s "escolha um Talento Racial"),
 * which may itself carry a choice — core's own {@code ExcepcionalidadeFeat#of} accepts an acquired
 * form there. {@code null} for every other Talento.
 *
 * <p>Nothing here is validated against {@code FeatCatalog} on write: this API layer never
 * constructs a core {@code Feat}, exactly as it never constructs a core {@code Race} (see {@link
 * RaceEntry}), and a name is passed through as given — the same contract {@code
 * attributeAbilities}/{@code activeAbilities}/{@link TitleEntry} already keep.
 */
public record FeatEntry(
        String type,
        List<String> choices,
        FeatEntry chosenFeat
) {
}
