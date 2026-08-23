package org.aventyrs.api.sheet;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.player.PlayerRepository;
import org.aventyrs.api.sheet.dto.AttributeValueDto;
import org.aventyrs.api.sheet.dto.AttributeValueResponse;
import org.aventyrs.api.sheet.dto.BleedingDto;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.CharacterResponse;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.CharacterSheetResponse;
import org.aventyrs.api.sheet.dto.CharacterSheetUpdateRequest;
import org.aventyrs.api.sheet.dto.CharacterSkillDto;
import org.aventyrs.api.sheet.dto.CharacterSkillResponse;
import org.aventyrs.api.sheet.dto.EgoValueDto;
import org.aventyrs.api.sheet.dto.EgoValueResponse;
import org.aventyrs.api.sheet.dto.LifeStealDto;
import org.aventyrs.api.sheet.dto.ManaDrainDto;
import org.aventyrs.api.sheet.dto.PendingEgoRecoveryDto;
import org.aventyrs.api.sheet.dto.RaceDto;
import org.aventyrs.api.sheet.dto.RaceResponse;
import org.aventyrs.api.sheet.dto.TemporaryBonusDto;
import org.aventyrs.api.sheet.dto.TitleDto;
import org.aventyrs.api.sheet.dto.TitleResponse;
import org.aventyrs.api.sheet.dto.WitheringDto;
import org.aventyrs.core.action.ActionPointsService;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.character.CharacterStatus;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.character.SizeCategory;
import org.aventyrs.core.character.services.DeterminationPointsService;
import org.aventyrs.core.character.services.FreeActionsService;
import org.aventyrs.core.character.services.HitPointsService;
import org.aventyrs.core.character.services.MagicPointsService;
import org.aventyrs.core.character.services.ReactionsService;
import org.aventyrs.core.skill.SkillType;
import org.springframework.stereotype.Service;

@Service
public class CharacterSheetService {

    private final CharacterSheetRepository repository;
    private final PlayerRepository playerRepository;

    public CharacterSheetService(CharacterSheetRepository repository, PlayerRepository playerRepository) {
        this.repository = repository;
        this.playerRepository = playerRepository;
    }

    public CharacterSheetResponse create(CharacterSheetCreateRequest request) {
        requirePlayerExists(request.playerId());

        CharacterSheetDocument document = new CharacterSheetDocument(
                UUID.randomUUID().toString(),
                toEntry(UUID.randomUUID().toString(), request.character()),
                request.playerId(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0,
                0,
                0,
                0,
                0,
                defaultTemporaryEgoPoints(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        return toResponse(repository.save(document));
    }

    public CharacterSheetResponse get(String id) {
        return toResponse(findOrThrow(id));
    }

    public List<CharacterSheetResponse> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public List<CharacterSheetResponse> listByPlayer(String playerId) {
        return repository.findByPlayerId(playerId).stream().map(this::toResponse).toList();
    }

    public CharacterSheetResponse update(String id, CharacterSheetUpdateRequest request) {
        CharacterSheetDocument document = findOrThrow(id);
        requirePlayerExists(request.playerId());

        document.setCharacter(toEntry(document.getCharacter().characterId(), request.character()));
        document.setPlayerId(request.playerId());
        document.setTotalExperience(request.totalExperience());
        document.setUnUsedExperience(request.unUsedExperience());
        document.setHitPointsSpent(request.hitPointsSpent());
        document.setMagicPointsSpent(request.magicPointsSpent());
        document.setDeterminationPointsSpent(request.determinationPointsSpent());
        document.setShieldPoints(request.shieldPoints());
        document.setFamaPositiva(request.famaPositiva());
        document.setFamaNegativa(request.famaNegativa());
        document.setTemporaryEgoPoints(normalizeTemporaryEgoPoints(request.temporaryEgoPoints()));
        document.setTemporaryBonuses(request.temporaryBonuses() == null ? List.of() : request.temporaryBonuses().stream()
                .map(bonus -> new TemporaryBonusEntry(bonus.type(), bonus.value(), bonus.remainingRounds()))
                .toList());
        document.setBleedingEffects(request.bleedingEffects() == null ? List.of() : request.bleedingEffects().stream()
                .map(bleeding -> new BleedingEntry(bleeding.valuePerRound(), bleeding.remainingRounds()))
                .toList());
        document.setManaDrains(request.manaDrains() == null ? List.of() : request.manaDrains().stream()
                .map(manaDrain -> new ManaDrainEntry(manaDrain.valuePerRound(), manaDrain.remainingRounds()))
                .toList());
        document.setWitheringEffects(request.witheringEffects() == null ? List.of() : request.witheringEffects().stream()
                .map(withering -> new WitheringEntry(withering.valuePerRound(), withering.remainingRounds()))
                .toList());
        document.setPendingEgoRecoveries(request.pendingEgoRecoveries() == null ? List.of() : request.pendingEgoRecoveries().stream()
                .map(recovery -> new PendingEgoRecoveryEntry(recovery.domain(), recovery.value(), recovery.minimumRestType()))
                .toList());
        document.setLifeSteals(request.lifeSteals() == null ? List.of() : request.lifeSteals().stream()
                .map(lifeSteal -> new LifeStealEntry(lifeSteal.value(), lifeSteal.remainingRounds()))
                .toList());
        document.setInventory(request.inventory() == null ? List.of() : request.inventory());

        return toResponse(repository.save(document));
    }

    /**
     * Writes just the two combat-state fields a live Cena changes — the damage taken and the
     * {@link CharacterStatus} tier it resolves to — leaving every other field on the sheet
     * exactly as stored.
     *
     * <p>Deliberately not routed through {@link #update}: that endpoint is a full overwrite, so a
     * caller holding a partially-populated {@code CharacterSheetUpdateRequest} would silently
     * reset everything it didn't resend (the same trap {@code RestCharacterRosterService}
     * documents on the client side). A token going from HIGH_LIFE to LOW_LIFE mid-scene must not
     * be able to wipe a character's inventory or títulos, so this reaches into the document
     * directly instead.
     *
     * <p>The rest of {@code character} is rebuilt from the stored entry rather than re-derived,
     * since {@link CharacterEntry} is a record — only {@code status} differs in the copy.
     */
    public void updateCombatStatus(String id, int hitPointsSpent, CharacterStatus status) {
        CharacterSheetDocument document = findOrThrow(id);
        CharacterEntry stored = document.getCharacter();

        document.setHitPointsSpent(hitPointsSpent);
        document.setCharacter(new CharacterEntry(
                stored.characterId(),
                stored.name(),
                stored.race(),
                stored.sexo(),
                stored.deity(),
                stored.tendencia(),
                stored.sizeCategory(),
                stored.actionProfile(),
                stored.attributes(),
                stored.egos(),
                stored.skills(),
                stored.attributeAbilities(),
                stored.egoAdvantages(),
                stored.activeAbilities(),
                stored.actionPoints(),
                stored.temporaryActionPointsBonus(),
                status,
                stored.reactions(),
                stored.freeActions(),
                stored.manaMultiplier(),
                stored.lifeMultiplier(),
                stored.determinationMultiplier(),
                stored.centelhaSuperiorSelected(),
                stored.feats(),
                stored.equipment(),
                stored.primaryTitle(),
                stored.secondaryTitle(),
                stored.tertiaryTitle()));

        repository.save(document);
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("CharacterSheet not found: " + id);
        }
        repository.deleteById(id);
    }

    private void requirePlayerExists(String playerId) {
        if (!playerRepository.existsById(playerId)) {
            throw new IllegalArgumentException("Player not found: " + playerId);
        }
    }

    private CharacterSheetDocument findOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("CharacterSheet not found: " + id));
    }

    /** 1 is core's own {@code Character#tendencia} default — the floor of its 1-10 scale. */
    private static final int DEFAULT_TENDENCIA = 1;

    /** core's own {@code AttributeValue#base} default. */
    private static final int DEFAULT_ATTRIBUTE_BASE = 1;

    /** core's own {@code EgoValue#base} default. */
    private static final int DEFAULT_EGO_BASE = 2;

    /** The 12 core {@code Race} implementations with no constructor state. */
    private static final Set<String> STATELESS_RACE_TYPES = Set.of(
            "HUMAN", "ELFO", "ANAO", "BESTIAL", "GIGANTES", "FADA", "FURIA",
            "GNOMO", "GORGONA", "PEQUENINO", "ORC", "SATIRO");

    /** The 7 {@code AbstractMesticoRace} subclasses plus {@code MeioElfo} — all require a parentRaceType. */
    private static final Set<String> STATEFUL_RACE_TYPES = Set.of(
            "AGASTIAS", "AQUAN", "COLOSSO", "DOLOS", "FLAMINIDEO", "INVERNAL",
            "NASCIDO_DA_FLORESTA", "MEIO_ELFO");

    private static CharacterEntry toEntry(String characterId, CharacterDto character) {
        int tendencia = character.tendencia() == null ? DEFAULT_TENDENCIA : character.tendencia();
        SizeCategory sizeCategory = character.sizeCategory() == null ? SizeCategory.ZERO : character.sizeCategory();
        CharacterStatus status = character.status() == null ? CharacterStatus.CLEAN : character.status();
        int actionPoints = character.actionPoints() == null ? ActionPointsService.DEFAULT_ACTION_POINTS : character.actionPoints();
        int temporaryActionPointsBonus = character.temporaryActionPointsBonus() == null ? 0 : character.temporaryActionPointsBonus();
        int reactions = character.reactions() == null ? ReactionsService.DEFAULT_REACTIONS : character.reactions();
        int freeActions = character.freeActions() == null ? FreeActionsService.DEFAULT_FREE_ACTIONS : character.freeActions();
        int manaMultiplier = character.manaMultiplier() == null ? MagicPointsService.DEFAULT_MANA_MULTIPLIER : character.manaMultiplier();
        int lifeMultiplier = character.lifeMultiplier() == null ? HitPointsService.DEFAULT_LIFE_MULTIPLIER : character.lifeMultiplier();
        int determinationMultiplier = character.determinationMultiplier() == null
                ? DeterminationPointsService.DEFAULT_DETERMINATION_MULTIPLIER : character.determinationMultiplier();
        boolean centelhaSuperiorSelected = character.centelhaSuperiorSelected() != null && character.centelhaSuperiorSelected();
        return new CharacterEntry(
                characterId,
                character.name(),
                toRaceEntry(character.race()),
                character.sexo(),
                character.deity(),
                tendencia,
                sizeCategory,
                character.actionProfile(),
                normalizeAttributes(character.attributes()),
                normalizeEgos(character.egos()),
                normalizeSkills(character.skills()),
                character.attributeAbilities() == null ? List.of() : character.attributeAbilities(),
                normalizeEgoAdvantages(character.egoAdvantages()),
                character.activeAbilities() == null ? List.of() : character.activeAbilities(),
                actionPoints,
                temporaryActionPointsBonus,
                status,
                reactions,
                freeActions,
                manaMultiplier,
                lifeMultiplier,
                determinationMultiplier,
                centelhaSuperiorSelected,
                character.feats() == null ? List.of() : character.feats(),
                character.equipment() == null ? List.of() : character.equipment(),
                toTitleEntry(character.primaryTitle()),
                toTitleEntry(character.secondaryTitle()),
                toTitleEntry(character.tertiaryTitle()));
    }

    private static TitleEntry toTitleEntry(TitleDto title) {
        if (title == null) {
            return null;
        }
        return new TitleEntry(
                title.type(),
                title.specializations() == null ? List.of() : title.specializations(),
                title.abilities() == null ? List.of() : title.abilities());
    }

    private static TitleResponse toTitleResponse(TitleEntry title) {
        if (title == null) {
            return null;
        }
        return new TitleResponse(
                title.type(),
                title.specializations() == null ? List.of() : title.specializations(),
                title.abilities() == null ? List.of() : title.abilities());
    }

    /**
     * The 8 stateful race types (see {@link #STATEFUL_RACE_TYPES}) require a {@code
     * parentRaceType} — everything else about them ({@code linhagem}, {@code
     * chosenInheritedAttribute}, the ability lists) is passed through as-is, since core's own
     * constructors are the ones that actually validate that state, and this API layer never
     * constructs a core {@code Race} instance (see {@link RaceEntry}'s javadoc).
     */
    private static RaceEntry toRaceEntry(RaceDto race) {
        if (STATEFUL_RACE_TYPES.contains(race.type())
                && (race.parentRaceType() == null || race.parentRaceType().isBlank())) {
            throw new IllegalArgumentException(race.type() + " requires a parentRaceType");
        }
        return new RaceEntry(
                race.type(),
                race.parentRaceType(),
                race.linhagem(),
                race.chosenInheritedAttribute(),
                race.inheritedRacialAbilities() == null ? List.of() : race.inheritedRacialAbilities(),
                race.inheritedAttributeAbilities() == null ? List.of() : race.inheritedAttributeAbilities());
    }

    /**
     * Null-guards the ability lists: legacy documents migrated from the old plain-string {@code
     * race} field (see the {@code 006-character-race-restructure} changelog) have those keys
     * absent entirely, which Spring Data binds to {@code null}, not an empty list.
     */
    private static RaceResponse toRaceResponse(RaceEntry race) {
        return new RaceResponse(
                race.type(),
                race.parentRaceType(),
                race.linhagem(),
                race.chosenInheritedAttribute(),
                race.inheritedRacialAbilities() == null ? List.of() : race.inheritedRacialAbilities(),
                race.inheritedAttributeAbilities() == null ? List.of() : race.inheritedAttributeAbilities());
    }

    /** Every {@link AttributeDomain} always has an entry, same as core's {@code CharacterAttributes}. */
    private static Map<AttributeDomain, AttributeValueEntry> normalizeAttributes(
            Map<AttributeDomain, AttributeValueDto> provided) {
        Map<AttributeDomain, AttributeValueEntry> attributes = new EnumMap<>(AttributeDomain.class);
        for (AttributeDomain domain : AttributeDomain.values()) {
            AttributeValueDto dto = provided == null ? null : provided.get(domain);
            int base = dto == null || dto.base() == null ? DEFAULT_ATTRIBUTE_BASE : dto.base();
            int racialBonus = dto == null || dto.racialBonus() == null ? 0 : dto.racialBonus();
            int variable = dto == null || dto.variable() == null ? 0 : dto.variable();
            attributes.put(domain, new AttributeValueEntry(base, racialBonus, variable));
        }
        return attributes;
    }

    /** Every {@link EgoDomain} always has an entry, same as core's {@code CharacterEgos}. */
    private static Map<EgoDomain, EgoValueEntry> normalizeEgos(Map<EgoDomain, EgoValueDto> provided) {
        Map<EgoDomain, EgoValueEntry> egos = new EnumMap<>(EgoDomain.class);
        for (EgoDomain domain : EgoDomain.values()) {
            EgoValueDto dto = provided == null ? null : provided.get(domain);
            int base = dto == null || dto.base() == null ? DEFAULT_EGO_BASE : dto.base();
            int variable = dto == null || dto.variable() == null ? 0 : dto.variable();
            egos.put(domain, new EgoValueEntry(base, variable));
        }
        return egos;
    }

    /** Unlike attributes, an absent {@link SkillType} key means untrained — nothing to default here. */
    private static Map<SkillType, CharacterSkillEntry> normalizeSkills(Map<SkillType, CharacterSkillDto> provided) {
        if (provided == null || provided.isEmpty()) {
            return Map.of();
        }
        Map<SkillType, CharacterSkillEntry> skills = new EnumMap<>(SkillType.class);
        provided.forEach((type, dto) -> skills.put(
                type, new CharacterSkillEntry(
                        dto.specializations() == null ? List.of() : dto.specializations(),
                        dto.competencyAbilities() == null ? List.of() : dto.competencyAbilities(),
                        dto.graduationValue() == null ? 0 : dto.graduationValue())));
        return skills;
    }

    /**
     * Unlike attributes/egos, an absent {@link EgoDomain} key means no Vantagem chosen —
     * nothing to default here, same reasoning as {@link #normalizeSkills}.
     */
    private static Map<EgoDomain, String> normalizeEgoAdvantages(Map<EgoDomain, String> provided) {
        if (provided == null || provided.isEmpty()) {
            return Map.of();
        }
        return new EnumMap<>(provided);
    }

    private static Map<EgoDomain, Integer> defaultTemporaryEgoPoints() {
        Map<EgoDomain, Integer> pools = new EnumMap<>(EgoDomain.class);
        for (EgoDomain domain : EgoDomain.values()) {
            pools.put(domain, 0);
        }
        return pools;
    }

    private static Map<EgoDomain, Integer> normalizeTemporaryEgoPoints(Map<EgoDomain, Integer> provided) {
        Map<EgoDomain, Integer> pools = defaultTemporaryEgoPoints();
        if (provided != null) {
            pools.putAll(provided);
        }
        return pools;
    }

    /**
     * {@code sizeCategory}/{@code attributes}/{@code egos}/{@code skills}/{@code
     * attributeAbilities}/{@code egoAdvantages}/{@code activeAbilities}/{@code status}/{@code
     * actionPoints}/{@code temporaryActionPointsBonus}/{@code reactions}/{@code freeActions}/
     * {@code manaMultiplier} fall back to defaults for documents persisted before those fields
     * existed, same reasoning as {@link #normalizeAttributes}.
     */
    private CharacterResponse toCharacterResponse(CharacterEntry character) {
        SizeCategory sizeCategory = character.sizeCategory() == null ? SizeCategory.ZERO : character.sizeCategory();
        Map<AttributeDomain, AttributeValueEntry> attributes =
                character.attributes() == null ? normalizeAttributes(null) : character.attributes();
        Map<EgoDomain, EgoValueEntry> egos = character.egos() == null ? normalizeEgos(null) : character.egos();
        Map<SkillType, CharacterSkillEntry> skills = character.skills() == null ? Map.of() : character.skills();
        CharacterStatus status = character.status() == null ? CharacterStatus.CLEAN : character.status();
        int actionPoints = character.actionPoints() == null ? ActionPointsService.DEFAULT_ACTION_POINTS : character.actionPoints();
        int temporaryActionPointsBonus = character.temporaryActionPointsBonus() == null ? 0 : character.temporaryActionPointsBonus();
        int reactions = character.reactions() == null ? ReactionsService.DEFAULT_REACTIONS : character.reactions();
        int freeActions = character.freeActions() == null ? FreeActionsService.DEFAULT_FREE_ACTIONS : character.freeActions();
        int manaMultiplier = character.manaMultiplier() == null ? MagicPointsService.DEFAULT_MANA_MULTIPLIER : character.manaMultiplier();
        int lifeMultiplier = character.lifeMultiplier() == null ? HitPointsService.DEFAULT_LIFE_MULTIPLIER : character.lifeMultiplier();
        int determinationMultiplier = character.determinationMultiplier() == null
                ? DeterminationPointsService.DEFAULT_DETERMINATION_MULTIPLIER : character.determinationMultiplier();
        boolean centelhaSuperiorSelected = character.centelhaSuperiorSelected() != null && character.centelhaSuperiorSelected();

        Map<AttributeDomain, AttributeValueResponse> attributesResponse = new EnumMap<>(AttributeDomain.class);
        attributes.forEach((domain, value) -> attributesResponse.put(domain, new AttributeValueResponse(
                value.base(), value.racialBonus(), value.variable(), value.base() + value.racialBonus() + value.variable())));

        Map<EgoDomain, EgoValueResponse> egosResponse = new EnumMap<>(EgoDomain.class);
        egos.forEach((domain, value) -> egosResponse.put(
                domain, new EgoValueResponse(value.base(), value.variable(), value.base() + value.variable())));

        Map<SkillType, CharacterSkillResponse> skillsResponse = new EnumMap<>(SkillType.class);
        skills.forEach((type, skill) -> skillsResponse.put(
                type, new CharacterSkillResponse(skill.specializations(), skill.competencyAbilities(), skill.graduationValue())));

        return new CharacterResponse(
                character.characterId(),
                character.name(),
                toRaceResponse(character.race()),
                character.sexo(),
                character.deity(),
                character.tendencia(),
                sizeCategory,
                character.actionProfile(),
                attributesResponse,
                egosResponse,
                skillsResponse,
                character.attributeAbilities() == null ? List.of() : character.attributeAbilities(),
                character.egoAdvantages() == null ? Map.of() : character.egoAdvantages(),
                character.activeAbilities() == null ? List.of() : character.activeAbilities(),
                actionPoints,
                temporaryActionPointsBonus,
                status,
                reactions,
                freeActions,
                manaMultiplier,
                lifeMultiplier,
                determinationMultiplier,
                centelhaSuperiorSelected,
                character.feats() == null ? List.of() : character.feats(),
                character.equipment() == null ? List.of() : character.equipment(),
                toTitleResponse(character.primaryTitle()),
                toTitleResponse(character.secondaryTitle()),
                toTitleResponse(character.tertiaryTitle()));
    }

    /**
     * {@code bleedingEffects}/{@code manaDrains}/{@code witheringEffects}/{@code
     * pendingEgoRecoveries} fall back to an empty list for documents persisted before those
     * fields existed, same reasoning as {@link #normalizeAttributes}.
     */
    private CharacterSheetResponse toResponse(CharacterSheetDocument document) {
        List<TemporaryBonusDto> bonuses = document.getTemporaryBonuses().stream()
                .map(bonus -> new TemporaryBonusDto(bonus.type(), bonus.value(), bonus.remainingRounds()))
                .toList();
        List<BleedingDto> bleedingEffects = document.getBleedingEffects() == null ? List.of() : document.getBleedingEffects().stream()
                .map(bleeding -> new BleedingDto(bleeding.valuePerRound(), bleeding.remainingRounds()))
                .toList();
        List<ManaDrainDto> manaDrains = document.getManaDrains() == null ? List.of() : document.getManaDrains().stream()
                .map(manaDrain -> new ManaDrainDto(manaDrain.valuePerRound(), manaDrain.remainingRounds()))
                .toList();
        List<WitheringDto> witheringEffects = document.getWitheringEffects() == null ? List.of() : document.getWitheringEffects().stream()
                .map(withering -> new WitheringDto(withering.valuePerRound(), withering.remainingRounds()))
                .toList();
        List<PendingEgoRecoveryDto> pendingEgoRecoveries = document.getPendingEgoRecoveries() == null ? List.of() : document.getPendingEgoRecoveries().stream()
                .map(recovery -> new PendingEgoRecoveryDto(recovery.domain(), recovery.value(), recovery.minimumRestType()))
                .toList();
        List<LifeStealDto> lifeSteals = document.getLifeSteals() == null ? List.of() : document.getLifeSteals().stream()
                .map(lifeSteal -> new LifeStealDto(lifeSteal.value(), lifeSteal.remainingRounds()))
                .toList();
        List<String> inventory = document.getInventory() == null ? List.of() : document.getInventory();
        CharacterResponse characterResponse = toCharacterResponse(document.getCharacter());
        return new CharacterSheetResponse(
                document.getId(),
                characterResponse,
                document.getPlayerId(),
                document.getTotalExperience(),
                document.getUnUsedExperience(),
                document.getHitPointsSpent(),
                document.getMagicPointsSpent(),
                document.getDeterminationPointsSpent(),
                document.getShieldPoints(),
                document.getFamaPositiva(),
                document.getFamaNegativa(),
                document.getTemporaryEgoPoints(),
                bonuses,
                bleedingEffects,
                manaDrains,
                witheringEffects,
                pendingEgoRecoveries,
                lifeSteals,
                inventory);
    }
}
