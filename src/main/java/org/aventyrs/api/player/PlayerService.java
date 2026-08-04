package org.aventyrs.api.player;

import java.util.List;
import java.util.UUID;
import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.player.dto.PlayerResponse;
import org.springframework.stereotype.Service;

@Service
public class PlayerService {

    private final PlayerRepository repository;

    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }

    public PlayerResponse create(PlayerRequest request) {
        PlayerDocument document = new PlayerDocument(UUID.randomUUID().toString(), request.name(), request.login());
        return toResponse(repository.save(document));
    }

    public PlayerResponse get(String id) {
        return toResponse(findOrThrow(id));
    }

    public List<PlayerResponse> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public PlayerResponse update(String id, PlayerRequest request) {
        PlayerDocument document = findOrThrow(id);
        document.setName(request.name());
        document.setLogin(request.login());
        return toResponse(repository.save(document));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Player not found: " + id);
        }
        repository.deleteById(id);
    }

    private PlayerDocument findOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Player not found: " + id));
    }

    private PlayerResponse toResponse(PlayerDocument document) {
        return new PlayerResponse(document.getId(), document.getName(), document.getLogin());
    }
}
