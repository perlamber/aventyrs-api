package org.aventyrs.api.sheet;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.player.PlayerRepository;
import org.aventyrs.api.sheet.dto.AttributeValueDto;
import org.aventyrs.api.sheet.dto.AttributeValueResponse;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.CharacterResponse;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.CharacterSheetResponse;
import org.aventyrs.api.sheet.dto.CharacterSheetUpdateRequest;
import org.aventyrs.api.sheet.dto.CharacterSkillDto;
import org.aventyrs.api.sheet.dto.CharacterSkillResponse;
import org.aventyrs.api.sheet.dto.EgoValueDto;
import org.aventyrs.api.sheet.dto.EgoValueResponse;
import org.aventyrs.api.sheet.dto.TemporaryBonusDto;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.character.SizeCategory;
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

    /** core's own {@code AttributeValue#base} default. */
    private static final int DEFAULT_ATTRIBUTE_BASE = 1;

    /** core's own {@code EgoValue#base} default. */
    private static final int DEFAULT_EGO_BASE = 2;

    private static CharacterEntry toEntry(String characterId, CharacterDto character) {
        int tendencia = character.tendencia() == null ? DEFAULT_TENDENCIA : character.tendencia();
        SizeCategory sizeCategory = character.sizeCategory() == null ? SizeCategory.ZERO : character.sizeCategory();
        return new CharacterEntry(
                characterId,
                character.name(),
                character.race(),
                character.sexo(),
                tendencia,
                sizeCategory,
                character.actionProfile(),
                normalizeAttributes(character.attributes()),
                normalizeEgos(character.egos()),
                normalizeSkills(character.skills()));
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
                        dto.graduationValue() == null ? 0 : dto.graduationValue())));
        return skills;
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
     * {@code sizeCategory}/{@code attributes}/{@code egos}/{@code skills} fall back to defaults
     * for documents persisted before those fields existed, same reasoning as
     * {@link #normalizeAttributes}.
     */
    private CharacterResponse toCharacterResponse(CharacterEntry character) {
        SizeCategory sizeCategory = character.sizeCategory() == null ? SizeCategory.ZERO : character.sizeCategory();
        Map<AttributeDomain, AttributeValueEntry> attributes =
                character.attributes() == null ? normalizeAttributes(null) : character.attributes();
        Map<EgoDomain, EgoValueEntry> egos = character.egos() == null ? normalizeEgos(null) : character.egos();
        Map<SkillType, CharacterSkillEntry> skills = character.skills() == null ? Map.of() : character.skills();

        Map<AttributeDomain, AttributeValueResponse> attributesResponse = new EnumMap<>(AttributeDomain.class);
        attributes.forEach((domain, value) -> attributesResponse.put(domain, new AttributeValueResponse(
                value.base(), value.racialBonus(), value.variable(), value.base() + value.racialBonus() + value.variable())));

        Map<EgoDomain, EgoValueResponse> egosResponse = new EnumMap<>(EgoDomain.class);
        egos.forEach((domain, value) -> egosResponse.put(
                domain, new EgoValueResponse(value.base(), value.variable(), value.base() + value.variable())));

        Map<SkillType, CharacterSkillResponse> skillsResponse = new EnumMap<>(SkillType.class);
        skills.forEach((type, skill) -> skillsResponse.put(
                type, new CharacterSkillResponse(skill.specializations(), skill.graduationValue())));

        return new CharacterResponse(
                character.characterId(),
                character.name(),
                character.race(),
                character.sexo(),
                character.tendencia(),
                sizeCategory,
                character.actionProfile(),
                attributesResponse,
                egosResponse,
                skillsResponse);
    }

    private CharacterSheetResponse toResponse(CharacterSheetDocument document) {
        List<TemporaryBonusDto> bonuses = document.getTemporaryBonuses().stream()
                .map(bonus -> new TemporaryBonusDto(bonus.type(), bonus.value(), bonus.remainingRounds()))
                .toList();
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
                bonuses);
    }
}
