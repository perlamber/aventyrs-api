package org.aventyrs.api.monster;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.monster.dto.MonsterSheetCreateRequest;
import org.aventyrs.api.monster.dto.MonsterSheetResponse;
import org.aventyrs.api.monster.dto.MonsterSheetUpdateRequest;
import org.aventyrs.api.player.PlayerRepository;
import org.aventyrs.api.sheet.CombatantSheetMapper;
import org.aventyrs.core.effect.CriticalEffectType;
import org.aventyrs.core.skill.DifficultyLevel;
import org.springframework.stereotype.Service;

@Service
public class MonsterSheetService {

    private final MonsterSheetRepository repository;
    private final PlayerRepository playerRepository;

    public MonsterSheetService(MonsterSheetRepository repository, PlayerRepository playerRepository) {
        this.repository = repository;
        this.playerRepository = playerRepository;
    }

    public MonsterSheetResponse create(MonsterSheetCreateRequest request) {
        requirePlayerExists(request.playerId());

        MonsterSheetDocument document = new MonsterSheetDocument(
                UUID.randomUUID().toString(),
                CombatantSheetMapper.toEntry(UUID.randomUUID().toString(), request.character()),
                request.playerId(),
                request.physicalDefense(),
                request.magicDefense(),
                normalizeAttackDifficulty(request.attackDifficulty()),
                request.attackBonus(),
                normalizeUndead(request.undead()),
                normalizeCriticalEffectImmunities(request.criticalEffectImmunities()),
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
                request.tokenImageUrl());
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

    public MonsterSheetResponse update(String id, MonsterSheetUpdateRequest request) {
        MonsterSheetDocument document = findOrThrow(id);
        requirePlayerExists(request.playerId());

        document.setCharacter(CombatantSheetMapper.toEntry(document.getCharacter().characterId(), request.character()));
        document.setPlayerId(request.playerId());
        document.setPhysicalDefense(request.physicalDefense());
        document.setMagicDefense(request.magicDefense());
        document.setAttackDifficulty(normalizeAttackDifficulty(request.attackDifficulty()));
        document.setAttackBonus(request.attackBonus());
        document.setUndead(normalizeUndead(request.undead()));
        document.setCriticalEffectImmunities(normalizeCriticalEffectImmunities(request.criticalEffectImmunities()));
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
        document.setInventory(request.inventory() == null ? List.of() : request.inventory());
        document.setTokenImageUrl(request.tokenImageUrl());

        return toResponse(repository.save(document));
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

    /** core's own {@code AbstractMonsterTemplate}'s {@code @Builder.Default}. */
    private static DifficultyLevel normalizeAttackDifficulty(DifficultyLevel attackDifficulty) {
        return attackDifficulty == null ? DifficultyLevel.MEDIUM : attackDifficulty;
    }

    private static boolean normalizeUndead(Boolean undead) {
        return undead != null && undead;
    }

    private static Set<CriticalEffectType> normalizeCriticalEffectImmunities(Set<CriticalEffectType> criticalEffectImmunities) {
        return criticalEffectImmunities == null ? Set.of() : criticalEffectImmunities;
    }

    private MonsterSheetResponse toResponse(MonsterSheetDocument document) {
        return new MonsterSheetResponse(
                document.getId(),
                CombatantSheetMapper.toCharacterResponse(document.getCharacter()),
                document.getPlayerId(),
                document.getPhysicalDefense(),
                document.getMagicDefense(),
                normalizeAttackDifficulty(document.getAttackDifficulty()),
                document.getAttackBonus(),
                document.isUndead(),
                normalizeCriticalEffectImmunities(document.getCriticalEffectImmunities()),
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
                document.getInventory() == null ? List.of() : document.getInventory(),
                document.getTokenImageUrl());
    }
}
