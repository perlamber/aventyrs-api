package org.aventyrs.api.sheet;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.player.PlayerRepository;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.CharacterSheetResponse;
import org.aventyrs.api.sheet.dto.CharacterSheetUpdateRequest;
import org.aventyrs.core.character.CharacterStatus;
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
                CombatantSheetMapper.toEntry(UUID.randomUUID().toString(), request.character()),
                request.playerId(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0,
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
                null);
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

        document.setCharacter(CombatantSheetMapper.toEntry(document.getCharacter().characterId(), request.character()));
        document.setPlayerId(request.playerId());
        document.setTotalExperience(request.totalExperience());
        document.setUnUsedExperience(request.unUsedExperience());
        document.setHitPointsSpent(request.hitPointsSpent());
        document.setMagicPointsSpent(request.magicPointsSpent());
        document.setDeterminationPointsSpent(request.determinationPointsSpent());
        document.setShieldPoints(request.shieldPoints());
        document.setFamaPositiva(request.famaPositiva());
        document.setFamaNegativa(request.famaNegativa());
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

    /**
     * {@code bleedingEffects}/{@code manaDrains}/{@code witheringEffects}/{@code
     * pendingEgoRecoveries} fall back to an empty list for documents persisted before those
     * fields existed, same reasoning as {@code CombatantSheetMapper#toCharacterResponse}.
     */
    private CharacterSheetResponse toResponse(CharacterSheetDocument document) {
        return new CharacterSheetResponse(
                document.getId(),
                CombatantSheetMapper.toCharacterResponse(document.getCharacter()),
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
