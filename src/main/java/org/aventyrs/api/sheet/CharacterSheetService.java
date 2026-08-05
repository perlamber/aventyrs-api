package org.aventyrs.api.sheet;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.player.PlayerRepository;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.CharacterResponse;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.CharacterSheetResponse;
import org.aventyrs.api.sheet.dto.CharacterSheetUpdateRequest;
import org.aventyrs.api.sheet.dto.TemporaryBonusDto;
import org.aventyrs.core.character.EgoDomain;
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

        return toResponse(repository.save(document));
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

    private static CharacterEntry toEntry(String characterId, CharacterDto character) {
        int tendencia = character.tendencia() == null ? DEFAULT_TENDENCIA : character.tendencia();
        return new CharacterEntry(characterId, character.name(), character.race(), character.sexo(), tendencia);
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

    private CharacterSheetResponse toResponse(CharacterSheetDocument document) {
        List<TemporaryBonusDto> bonuses = document.getTemporaryBonuses().stream()
                .map(bonus -> new TemporaryBonusDto(bonus.type(), bonus.value(), bonus.remainingRounds()))
                .toList();
        CharacterEntry character = document.getCharacter();
        CharacterResponse characterResponse = new CharacterResponse(
                character.characterId(), character.name(), character.race(), character.sexo(), character.tendencia());
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
                bonuses);
    }
}
