package org.aventyrs.api.monster;

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

    public MonsterSheetService(MonsterSheetRepository repository, PlayerRepository playerRepository) {
        this.repository = repository;
        this.playerRepository = playerRepository;
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
                null);
        return toResponse(repository.save(document));
    }

    /**
     * Stores an invoked creature under id, run by playerId — its caster's player (client 0.0.95). Its {@code
     * character} is derived from the creature core rebuilds from summon, as a foe's is from its blueprint.
     */
    public MonsterSheetResponse createSummon(String id, SummonEntry summon, String playerId) {
        MonsterSheet sheet = summonTemplate(summon).spawn(new Player());
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
                summon);
        return toResponse(repository.save(document));
    }

    private static org.aventyrs.core.monster.summon.NatureSummon summonTemplate(SummonEntry summon) {
        return org.aventyrs.core.monster.summon.NatureSummon.restore(summon.kind(), summon.conjuradorManaGraduation(),
                summon.powers());
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
            java.util.Map<org.aventyrs.core.character.EgoDomain, Integer> temporaryEgoPoints) {
        MonsterSheetDocument document = findOrThrow(id);
        document.setHitPointsSpent(hitPointsSpent);
        document.setMagicPointsSpent(magicPointsSpent);
        document.setDeterminationPointsSpent(determinationPointsSpent);
        if (temporaryEgoPoints != null) {
            document.setTemporaryEgoPoints(CombatantSheetMapper.normalizeTemporaryEgoPoints(temporaryEgoPoints));
        }
        repository.save(document);
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
        MonsterSheet sheet = summon == null ? blueprint.spawn(new Player()) : summonTemplate(summon).spawn(new Player());
        Character character = sheet.getCharacter();
        return new MonsterSheetResponse(
                document.getId(),
                summon == null ? MonsterBlueprintMapper.toDto(document.getBlueprint()) : null,
                CombatantSheetMapper.toCharacterResponse(document.getCharacter()),
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
                        summon.conjuradorManaGraduation(), summon.powers(), summon.casterCharacterSheetId()));
    }
}
