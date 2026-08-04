package org.aventyrs.api.scene;

import java.util.List;
import java.util.UUID;
import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.scene.dto.GridPositionDto;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.api.scene.dto.SceneParticipantRequest;
import org.aventyrs.api.scene.dto.SceneParticipantResponse;
import org.aventyrs.api.scene.dto.SceneResponse;
import org.aventyrs.api.scene.dto.SceneUpdateRequest;
import org.aventyrs.core.scene.grid.GridPosition;
import org.aventyrs.api.sheet.CharacterSheetRepository;
import org.springframework.stereotype.Service;

@Service
public class SceneService {

    private final SceneRepository repository;
    private final CharacterSheetRepository characterSheetRepository;

    public SceneService(SceneRepository repository, CharacterSheetRepository characterSheetRepository) {
        this.repository = repository;
        this.characterSheetRepository = characterSheetRepository;
    }

    public SceneResponse create(SceneCreateRequest request) {
        SceneDocument document = new SceneDocument(UUID.randomUUID().toString(), request.name(), List.of(), 0, -1);
        return toResponse(repository.save(document));
    }

    public SceneResponse get(String id) {
        return toResponse(findOrThrow(id));
    }

    public List<SceneResponse> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public SceneResponse update(String id, SceneUpdateRequest request) {
        SceneDocument document = findOrThrow(id);

        List<SceneParticipantEntry> participants = request.participants().stream()
                .map(this::toEntry)
                .toList();
        requireParticipantsExist(participants);
        requireDistinctPositions(participants);
        requireValidTurnCursor(request.currentIndex(), participants.size());

        document.setName(request.name());
        document.setParticipants(participants);
        document.setCurrentRound(request.currentRound());
        document.setCurrentIndex(request.currentIndex());

        return toResponse(repository.save(document));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Scene not found: " + id);
        }
        repository.deleteById(id);
    }

    private void requireParticipantsExist(List<SceneParticipantEntry> participants) {
        for (SceneParticipantEntry participant : participants) {
            if (!characterSheetRepository.existsById(participant.characterSheetId())) {
                throw new IllegalArgumentException("CharacterSheet not found: " + participant.characterSheetId());
            }
        }
    }

    private void requireDistinctPositions(List<SceneParticipantEntry> participants) {
        long distinctPositions = participants.stream().map(SceneParticipantEntry::position).distinct().count();
        if (distinctPositions != participants.size()) {
            throw new IllegalArgumentException("Two participants cannot occupy the same grid position");
        }
    }

    private void requireValidTurnCursor(int currentIndex, int participantCount) {
        int maxValidIndex = participantCount - 1;
        if (currentIndex < -1 || currentIndex > maxValidIndex) {
            throw new IllegalArgumentException("currentIndex must be between -1 and " + maxValidIndex);
        }
    }

    private SceneParticipantEntry toEntry(SceneParticipantRequest request) {
        return new SceneParticipantEntry(
                request.characterSheetId(),
                request.initiativeValue(),
                request.group(),
                new GridPosition(request.position().x(), request.position().y()));
    }

    private SceneDocument findOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Scene not found: " + id));
    }

    private SceneResponse toResponse(SceneDocument document) {
        List<SceneParticipantResponse> participants = document.getParticipants().stream()
                .map(entry -> new SceneParticipantResponse(
                        entry.characterSheetId(),
                        entry.initiativeValue(),
                        entry.group(),
                        new GridPositionDto(entry.position().x(), entry.position().y())))
                .toList();
        return new SceneResponse(
                document.getId(),
                document.getName(),
                participants,
                document.getCurrentRound(),
                document.getCurrentIndex());
    }
}
