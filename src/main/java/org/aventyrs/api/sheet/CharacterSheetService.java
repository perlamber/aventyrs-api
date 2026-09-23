package org.aventyrs.api.sheet;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.aventyrs.api.campaign.CampaignService;
import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.item.InventoryItemMapper;
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
    private final CampaignService campaignService;

    public CharacterSheetService(CharacterSheetRepository repository, PlayerRepository playerRepository,
            CampaignService campaignService) {
        this.repository = repository;
        this.playerRepository = playerRepository;
        this.campaignService = campaignService;
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
                null);
        return toResponse(repository.save(document));
    }

    public CharacterSheetResponse get(String id) {
        return toResponse(findOrThrow(id));
    }

    public List<CharacterSheetResponse> list() {
        return toResponses(repository.findAll());
    }

    public List<CharacterSheetResponse> listByPlayer(String playerId) {
        return toResponses(repository.findByPlayerId(playerId));
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
        document.setEquipmentPoints(request.equipmentPoints());
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
    public void updateCombatStatus(String id, int hitPointsSpent, int magicPointsSpent,
            int determinationPointsSpent, CharacterStatus status) {
        CharacterSheetDocument document = findOrThrow(id);
        CharacterEntry stored = document.getCharacter();

        // All three pools, not PV alone: activating a Habilidade de Título spends PD (and some are
        // priced in PV instead), so a Cena that only ever persisted damage silently refunded every
        // PD spent the moment a player reconnected.
        document.setHitPointsSpent(hitPointsSpent);
        document.setMagicPointsSpent(magicPointsSpent);
        document.setDeterminationPointsSpent(determinationPointsSpent);
        document.setCharacter(new CharacterEntry(
                stored.characterId(),
                stored.name(),
                stored.race(),
                stored.sexo(),
                stored.deity(),
                stored.alignment(),
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
                stored.tertiaryTitle(),
                stored.spells(),
                stored.mimetizedSpells()));

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

    /** Answers every sheet's progression lock from one Campanha query rather than one per sheet. */
    private List<CharacterSheetResponse> toResponses(List<CharacterSheetDocument> documents) {
        Set<String> locked = campaignService.progressionLockedCampaignIds();
        return documents.stream()
                .map(document -> toResponse(document, locked.contains(document.getCampaignId())))
                .toList();
    }

    private CharacterSheetResponse toResponse(CharacterSheetDocument document) {
        return toResponse(document, campaignService.isProgressionLocked(document.getCampaignId()));
    }

    /**
     * {@code bleedingEffects}/{@code manaDrains}/{@code witheringEffects}/{@code
     * pendingEgoRecoveries} fall back to an empty list for documents persisted before those
     * fields existed, same reasoning as {@code CombatantSheetMapper#toCharacterResponse}.
     */
    private CharacterSheetResponse toResponse(CharacterSheetDocument document, boolean progressionLocked) {
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
                document.getEquipmentPoints(),
                document.getTemporaryEgoPoints(),
                CombatantSheetMapper.toTemporaryBonusDtos(document.getTemporaryBonuses()),
                CombatantSheetMapper.toBleedingDtos(document.getBleedingEffects()),
                CombatantSheetMapper.toManaDrainDtos(document.getManaDrains()),
                CombatantSheetMapper.toWitheringDtos(document.getWitheringEffects()),
                CombatantSheetMapper.toPendingEgoRecoveryDtos(document.getPendingEgoRecoveries()),
                CombatantSheetMapper.toLifeStealDtos(document.getLifeSteals()),
                InventoryItemMapper.toDtos(document.getInventory()),
                document.getTokenImageUrl(),
                document.getCampaignId(),
                progressionLocked);
    }
}
