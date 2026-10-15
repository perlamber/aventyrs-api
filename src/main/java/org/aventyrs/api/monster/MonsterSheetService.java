package org.aventyrs.api.monster;

import java.util.EnumSet;
import java.util.Set;
import org.aventyrs.api.monster.dto.ConjurePreviewRequest;
import org.aventyrs.core.monster.MonsterKind;
import org.aventyrs.core.monster.generator.ConjurePreset;
import org.aventyrs.core.monster.generator.MonsterGenerator;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.UUID;
import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.item.InventoryItemMapper;
import org.aventyrs.api.monster.dto.MonsterSheetCreateRequest;
import org.aventyrs.api.monster.dto.MonsterSheetResponse;
import org.aventyrs.api.monster.dto.MonsterSheetUpdateRequest;
import org.aventyrs.api.player.PlayerRepository;
import org.aventyrs.api.sheet.CombatantSheetMapper;
import org.aventyrs.core.action.ActionPointsService;
import org.aventyrs.core.action.ActionPointsServiceImpl;
import org.aventyrs.core.character.Character;
import org.aventyrs.core.character.DefenseType;
import org.aventyrs.core.character.services.DeterminationPointsService;
import org.aventyrs.core.character.services.DeterminationPointsServiceImpl;
import org.aventyrs.core.character.services.HitPointsService;
import org.aventyrs.core.character.services.HitPointsServiceImpl;
import org.aventyrs.core.character.services.MagicPointsService;
import org.aventyrs.core.character.services.MagicPointsServiceImpl;
import org.aventyrs.core.monster.MonsterBlueprint;
import org.aventyrs.core.monster.MonsterRules;
import org.aventyrs.core.monster.MonsterSheet;
import org.aventyrs.core.monster.MonsterViolation;
import org.aventyrs.core.sheet.Player;
import org.springframework.stereotype.Service;

/**
 * Monster sheets, built by core's creation rules. A write stores the Mestre's blueprint (refused
 * when {@link MonsterRules#validate} objects) and a {@code character} core derives from it; a read
 * spawns the blueprint in core and reports every derived number beside it. See {@link
 * MonsterSheetDocument}.
 */
@Service
public class MonsterSheetService {

    /** The turn number {@code ActionPointsService} is asked about — the monster's PA at rest. */
    private static final int AT_REST_TURN = 1;

    private final MonsterSheetRepository repository;
    private final PlayerRepository playerRepository;
    private final HitPointsService hitPointsService = new HitPointsServiceImpl();
    private final DeterminationPointsService determinationPointsService = new DeterminationPointsServiceImpl();
    private final MagicPointsService magicPointsService = new MagicPointsServiceImpl();
    private final ActionPointsService actionPointsService = new ActionPointsServiceImpl();

    /**
     * Field-level writes for what a status frame and a dismissal touch at the same moment (core 0.1.7): a corpse-raised
     * undead's fall sends both its status and its dismissal, and a whole-document save of either used to erase the
     * other's change — the body lost its corpse mark.
     */
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    public MonsterSheetService(MonsterSheetRepository repository, PlayerRepository playerRepository,
            org.springframework.data.mongodb.core.MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.playerRepository = playerRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /** Sets update's fields on sheet id alone — nothing else on the document is read or rewritten. */
    private void setFields(String id, org.springframework.data.mongodb.core.query.Update update) {
        findOrThrow(id);
        mongoTemplate.updateFirst(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("_id").is(id)), update,
                MonsterSheetDocument.class);
    }

    public MonsterSheetResponse create(MonsterSheetCreateRequest request) {
        requirePlayerExists(request.playerId());
        MonsterBlueprintEntry blueprint = MonsterBlueprintMapper.toEntry(request.blueprint());

        MonsterSheetDocument document = new MonsterSheetDocument(
                UUID.randomUUID().toString(),
                blueprint,
                deriveCharacter(UUID.randomUUID().toString(), blueprint),
                request.playerId(),
                0,
                0,
                0,
                0,
                CombatantSheetMapper.defaultTemporaryEgoPoints(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                InventoryItemMapper.toEntries(request.inventory()),
                request.tokenImageUrl(),
                null,
                null,
                List.of(),
                null,
                null,
                request.campaignId(),
                null,
                null);
        return toResponse(repository.save(document));
    }

    /**
     * Stores an invoked creature under id, run by playerId — its caster's player (client 0.0.95). Its {@code
     * character} is derived from the creature core rebuilds from summon, as a foe's is from its blueprint.
     */
    public MonsterSheetResponse createSummon(String id, SummonEntry summon, String playerId) {
        return createSummon(id, summon, playerId, 0);
    }

    /**
     * Marks the sheet id as a body left behind (core 0.1.6.24) — a corpse-raised undead that fell or whose Magia ended,
     * raisable again. Nothing happens to a sheet that no longer exists.
     */
    public void markCorpse(String id) {
        if (repository.existsById(id)) {
            setFields(id, new org.springframework.data.mongodb.core.query.Update().set("corpse", true));
        }
    }

    /**
     * {@link #createSummon(String, SummonEntry, String)} for a body raised timesRaised times (core 0.1.6.24) — an undead
     * raised from a corpse carries the corpse's count plus one.
     */
    public MonsterSheetResponse createSummon(String id, SummonEntry summon, String playerId, int timesRaised) {
        MonsterSheet sheet = summon.spawn(new Player());
        MonsterSheetDocument document = new MonsterSheetDocument(
                id,
                null,
                MonsterBlueprintMapper.toCharacterEntry(UUID.randomUUID().toString(), sheet.getCharacter(), null),
                playerId,
                0,
                0,
                0,
                0,
                CombatantSheetMapper.defaultTemporaryEgoPoints(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                summon,
                null,
                List.of(),
                timesRaised,
                null,
                null,
                null,
                null);
        return toResponse(repository.save(document));
    }


    public MonsterSheetResponse get(String id) {
        return toResponse(findOrThrow(id));
    }

    public List<MonsterSheetResponse> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public List<MonsterSheetResponse> listByPlayer(String playerId) {
        return repository.findByPlayerId(playerId).stream().map(this::toResponse).toList();
    }

    // ---- Bestiário scoping and conjuring (core 0.1.7.5) ---------------------------------------

    /** What {@code ?campaign=} on the Bestiário names: every foe, the lore ones only, or one Campanha's. */
    public static final String ALL_CAMPAIGNS = "all";
    public static final String LORE = "lore";

    /** A status no picker offers: the foe already fell. */
    private static final Set<org.aventyrs.core.character.CharacterStatus> FALLEN = EnumSet.of(
            org.aventyrs.core.character.CharacterStatus.FALLEN,
            org.aventyrs.core.character.CharacterStatus.COMMA,
            org.aventyrs.core.character.CharacterStatus.DEAD);

    /**
     * The Bestiário: every foe a Mestre authored. Summons and conjured foes are never listed. campaign is {@link
     * #ALL_CAMPAIGNS} (or null), {@link #LORE} for the foes of no Campanha, or a Campanha id for that Campanha's own.
     */
    public List<MonsterSheetResponse> listBestiary(String campaign) {
        Criteria criteria = bestiaryCriteria();
        if (LORE.equals(campaign)) {
            criteria = criteria.and("campaignId").is(null);
        } else if (campaign != null && !campaign.isBlank() && !ALL_CAMPAIGNS.equals(campaign)) {
            criteria = criteria.and("campaignId").is(campaign);
        }
        return mongoTemplate.find(Query.query(criteria), MonsterSheetDocument.class).stream()
                .map(this::toResponse).toList();
    }

    /**
     * The Bestiário foes a Mestre may place in sceneId (table ruling, 2026-10-15):
     * <ul>
     *   <li>those of campaignId plus the lore ones (lore only when campaignId is null);</li>
     *   <li>none that fell;</li>
     *   <li>none already standing in any other Cena. Placement there is derived from the Cenas' own participants and
     *   never stored.</li>
     * </ul>
     */
    public List<MonsterSheetResponse> listPlaceable(String campaignId, String sceneId) {
        Criteria criteria = bestiaryCriteria();
        criteria = campaignId == null || campaignId.isBlank()
                ? criteria.and("campaignId").is(null)
                : criteria.orOperator(Criteria.where("campaignId").is(null), Criteria.where("campaignId").is(campaignId));
        Set<String> elsewhere = Set.copyOf(placedOutside(sceneId));
        return mongoTemplate.find(Query.query(criteria), MonsterSheetDocument.class).stream()
                .filter(document -> document.getStatus() == null || !FALLEN.contains(document.getStatus()))
                .filter(document -> !elsewhere.contains(document.getId()))
                .map(this::toResponse)
                .toList();
    }

    /** Every combatant id standing in a Cena other than sceneId (every Cena when it is null). */
    public List<String> placedOutside(String sceneId) {
        Query query = sceneId == null ? new Query() : Query.query(Criteria.where("_id").ne(sceneId));
        return mongoTemplate.findDistinct(query, "participants.characterSheetId",
                org.aventyrs.api.scene.SceneDocument.class, String.class);
    }

    private static Criteria bestiaryCriteria() {
        return Criteria.where("summon").is(null).and("blueprint").ne(null).and("conjured").ne(true);
    }

    /**
     * A monster from {@link MonsterGenerator}, shown and never stored. It is the conjure dialog's preview, and the
     * very blueprint {@link #createConjured} stores for the same inputs.
     */
    public MonsterSheetResponse preview(ConjurePreviewRequest request) {
        MonsterBlueprint blueprint = generate(request.preset(), request.powerDegree(), request.kind(), request.name(),
                request.seed());
        return toResponse(conjuredDocument("preview", blueprint, null, null, null, null));
    }

    /** {@link MonsterGenerator#generate}, refusing a GP outside the conjure dialog's range. */
    public static MonsterBlueprint generate(ConjurePreset preset, int powerDegree, MonsterKind kind, String name,
            long seed) {
        if (preset == null) {
            throw new IllegalArgumentException("A conjure preset is required");
        }
        if (powerDegree < 0 || powerDegree > MAXIMUM_CONJURE_POWER_DEGREE) {
            throw new IllegalArgumentException("Grau de Poder must be 0.." + MAXIMUM_CONJURE_POWER_DEGREE + ": " + powerDegree);
        }
        return MonsterGenerator.generate(preset, powerDegree, kind == null ? MonsterKind.REGULAR : kind, name, seed);
    }

    /** The highest GP the conjure dialog offers; Abominação starts at 61. */
    public static final int MAXIMUM_CONJURE_POWER_DEGREE = 80;

    /**
     * Stores a conjured foe for playerId, standing in sceneId: never in the Bestiário, and deleted when it leaves the
     * Cena ({@link #deleteConjured}).
     */
    public MonsterSheetDocument createConjured(MonsterBlueprint blueprint, String playerId, String campaignId,
            String sceneId, String tokenImageUrl) {
        return repository.save(conjuredDocument(UUID.randomUUID().toString(), blueprint, playerId, campaignId, sceneId,
                tokenImageUrl));
    }

    /** A conjured foe's document, not yet stored: everything a fresh sheet holds, empty. */
    private static MonsterSheetDocument conjuredDocument(String id, MonsterBlueprint blueprint, String playerId,
            String campaignId, String sceneId, String tokenImageUrl) {
        MonsterBlueprintEntry entry = MonsterBlueprintMapper.fromCore(blueprint);
        MonsterSheetDocument document = new MonsterSheetDocument();
        document.setId(id);
        document.setBlueprint(entry);
        document.setCharacter(deriveCharacter(UUID.randomUUID().toString(), entry));
        document.setPlayerId(playerId);
        document.setTemporaryEgoPoints(CombatantSheetMapper.defaultTemporaryEgoPoints());
        document.setTemporaryBonuses(List.of());
        document.setBleedingEffects(List.of());
        document.setManaDrains(List.of());
        document.setWitheringEffects(List.of());
        document.setPendingEgoRecoveries(List.of());
        document.setLifeSteals(List.of());
        document.setInventory(List.of());
        document.setSubordinates(List.of());
        document.setTokenImageUrl(tokenImageUrl);
        document.setCampaignId(campaignId == null || campaignId.isBlank() ? null : campaignId);
        document.setConjured(true);
        document.setConjuredInSceneId(sceneId);
        return document;
    }

    /** Whether id names a conjured foe. */
    public boolean isConjured(String id) {
        return repository.findById(id).map(document -> Boolean.TRUE.equals(document.getConjured())).orElse(false);
    }

    /** A conjured foe that travelled now stands in sceneId. */
    public void moveConjured(String id, String sceneId) {
        setFields(id, new org.springframework.data.mongodb.core.query.Update().set("conjuredInSceneId", sceneId));
    }

    /** Deletes id if it is a conjured foe, which is what leaving its Cena means for one. */
    public void deleteConjured(String id) {
        if (isConjured(id)) {
            repository.deleteById(id);
        }
    }

    /** Deletes every conjured foe standing in sceneId: the Cena itself is going. */
    public void deleteConjuredIn(String sceneId) {
        mongoTemplate.remove(Query.query(Criteria.where("conjured").is(true).and("conjuredInSceneId").is(sceneId)),
                MonsterSheetDocument.class);
    }

    public MonsterSheetResponse update(String id, MonsterSheetUpdateRequest request) {
        MonsterSheetDocument document = findOrThrow(id);
        requirePlayerExists(request.playerId());
        MonsterBlueprintEntry blueprint = MonsterBlueprintMapper.toEntry(request.blueprint());

        document.setBlueprint(blueprint);
        document.setCharacter(deriveCharacter(document.getCharacter().characterId(), blueprint));
        document.setPlayerId(request.playerId());
        document.setHitPointsSpent(request.hitPointsSpent());
        document.setMagicPointsSpent(request.magicPointsSpent());
        document.setDeterminationPointsSpent(request.determinationPointsSpent());
        document.setShieldPoints(request.shieldPoints());
        document.setTemporaryEgoPoints(CombatantSheetMapper.normalizeTemporaryEgoPoints(request.temporaryEgoPoints()));
        document.setTemporaryBonuses(CombatantSheetMapper.toTemporaryBonusEntries(request.temporaryBonuses()));
        document.setBleedingEffects(CombatantSheetMapper.toBleedingEntries(request.bleedingEffects()));
        document.setManaDrains(CombatantSheetMapper.toManaDrainEntries(request.manaDrains()));
        document.setWitheringEffects(CombatantSheetMapper.toWitheringEntries(request.witheringEffects()));
        document.setPendingEgoRecoveries(CombatantSheetMapper.toPendingEgoRecoveryEntries(request.pendingEgoRecoveries()));
        document.setLifeSteals(CombatantSheetMapper.toLifeStealEntries(request.lifeSteals()));
        document.setInventory(InventoryItemMapper.toEntries(request.inventory()));
        document.setTokenImageUrl(request.tokenImageUrl());
        document.setCampaignId(request.campaignId());

        return toResponse(repository.save(document));
    }

    /** Whether id names a monster sheet — a Scene participant id may name either kind. */
    public boolean exists(String id) {
        return repository.existsById(id);
    }

    /**
     * A foe's pools as its Cena left them — the {@code /status} frame's half that applies to a
     * monster (no hourly Ego debt, no exhaustion: nothing on a monster produces either). {@code
     * temporaryEgoPoints} is written only when sent, same contract as {@code
     * CharacterSheetService#updateCombatStatus}.
     */
    public void updateCombatStatus(String id, int hitPointsSpent, int magicPointsSpent, int determinationPointsSpent,
            org.aventyrs.core.character.CharacterStatus status,
            java.util.Map<org.aventyrs.core.character.EgoDomain, Integer> temporaryEgoPoints) {
        org.springframework.data.mongodb.core.query.Update update = new org.springframework.data.mongodb.core.query.Update()
                .set("hitPointsSpent", hitPointsSpent)
                .set("magicPointsSpent", magicPointsSpent)
                .set("determinationPointsSpent", determinationPointsSpent);
        // Without this a player joining the Cena reads the blueprint-derived CLEAN and draws no badge.
        if (status != null) {
            update.set("status", status);
        }
        if (temporaryEgoPoints != null) {
            update.set("temporaryEgoPoints", CombatantSheetMapper.normalizeTemporaryEgoPoints(temporaryEgoPoints));
        }
        setFields(id, update);
    }

    /** Replaces the foe's running Sangramentos with what its owner's client reports (core's {@code Bleeding}). */
    public void updateBleeding(String id, java.util.List<org.aventyrs.api.sheet.dto.BleedingDto> bleedingEffects) {
        setFields(id, new org.springframework.data.mongodb.core.query.Update()
                .set("bleedingEffects", CombatantSheetMapper.toBleedingEntries(bleedingEffects)));
    }

    /** The Subordinados this foe commands, as the GM's status frame reports them (core 0.1.5.6). */
    public void updateSubordinates(String id, List<org.aventyrs.api.sheet.dto.SubordinateDto> subordinates) {
        setFields(id, new org.springframework.data.mongodb.core.query.Update().set("subordinates", List.copyOf(subordinates)));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("MonsterSheet not found: " + id);
        }
        repository.deleteById(id);
    }

    private MonsterSheetDocument findOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("MonsterSheet not found: " + id));
    }

    private void requirePlayerExists(String playerId) {
        if (!playerRepository.existsById(playerId)) {
            throw new IllegalArgumentException("Player not found: " + playerId);
        }
    }

    /**
     * Validates the blueprint in core and derives its {@code character}.
     *
     * @throws MonsterRulesViolationException when core finds the blueprint illegal
     */
    private static org.aventyrs.api.sheet.CharacterEntry deriveCharacter(String characterId, MonsterBlueprintEntry blueprint) {
        MonsterBlueprint core = MonsterBlueprintMapper.toCore(blueprint);
        List<MonsterViolation> violations = MonsterRules.validate(core);
        if (!violations.isEmpty()) {
            throw new MonsterRulesViolationException(violations);
        }
        return MonsterBlueprintMapper.toCharacterEntry(characterId, core.buildCharacter(), blueprint);
    }

    private MonsterSheetResponse toResponse(MonsterSheetDocument document) {
        // An invoked creature has no blueprint: core rebuilds it from its summon descriptor.
        SummonEntry summon = document.getSummon();
        MonsterBlueprint blueprint = summon == null ? MonsterBlueprintMapper.toCore(document.getBlueprint()) : null;
        MonsterSheet sheet = summon == null ? blueprint.spawn(new Player()) : summon.spawn(new Player());
        Character character = sheet.getCharacter();
        return new MonsterSheetResponse(
                document.getId(),
                summon == null ? MonsterBlueprintMapper.toDto(document.getBlueprint()) : null,
                CombatantSheetMapper.toCharacterResponse(document.getStatus() == null
                        ? document.getCharacter()
                        : document.getCharacter().withStatus(document.getStatus())),
                document.getPlayerId(),
                blueprint == null ? null : blueprint.getCategory(),
                sheet.getDefense(DefenseType.PHYSICAL),
                sheet.getDefense(DefenseType.MAGIC),
                sheet.getGeneralDifficulty(),
                sheet.getSkillDifficulties(),
                hitPointsService.getMaxHitPoints(character, sheet),
                determinationPointsService.getMaxDeterminationPoints(character, sheet),
                magicPointsService.getMaxMagicPoints(character, sheet),
                actionPointsService.getMaxActionPoints(sheet, AT_REST_TURN),
                sheet.isUndead(),
                blueprint == null ? sheet.getCriticalEffectImmunities() : blueprint.getCriticalEffectImmunities(),
                blueprint == null ? List.of() : MonsterRules.validate(blueprint),
                document.getHitPointsSpent(),
                document.getMagicPointsSpent(),
                document.getDeterminationPointsSpent(),
                document.getShieldPoints(),
                document.getTemporaryEgoPoints(),
                CombatantSheetMapper.toTemporaryBonusDtos(document.getTemporaryBonuses()),
                CombatantSheetMapper.toBleedingDtos(document.getBleedingEffects()),
                CombatantSheetMapper.toManaDrainDtos(document.getManaDrains()),
                CombatantSheetMapper.toWitheringDtos(document.getWitheringEffects()),
                CombatantSheetMapper.toPendingEgoRecoveryDtos(document.getPendingEgoRecoveries()),
                CombatantSheetMapper.toLifeStealDtos(document.getLifeSteals()),
                InventoryItemMapper.toDtos(document.getInventory()),
                document.getTokenImageUrl(),
                summon == null ? null : new org.aventyrs.api.monster.dto.SummonDto(summon.kind(),
                        summon.conjuradorManaGraduation(), summon.powers(), summon.casterCharacterSheetId(),
                        summon.enhancement(), summon.familiar(), summon.bodySize(), summon.butcher(),
                        summon.uncontrolled()),
                document.getSubordinates() == null ? List.of() : List.copyOf(document.getSubordinates()),
                document.getTimesRaised() == null ? 0 : document.getTimesRaised(),
                Boolean.TRUE.equals(document.getCorpse()),
                document.getCampaignId(),
                Boolean.TRUE.equals(document.getConjured()),
                document.getConjuredInSceneId());
    }
}
