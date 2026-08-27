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
        PlayerDocument document = new PlayerDocument(
                UUID.randomUUID().toString(), request.name(), request.login(), roleOf(request.role()));
        return toResponse(repository.save(document));
    }

    public PlayerResponse get(String id) {
        return toResponse(findOrThrow(id));
    }

    public PlayerResponse getByLogin(String login) {
        return repository.findByLogin(login)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Player not found: " + login));
    }

    public List<PlayerResponse> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public PlayerResponse update(String id, PlayerRequest request) {
        PlayerDocument document = findOrThrow(id);
        document.setName(request.name());
        document.setLogin(request.login());
        document.setRole(roleOf(request.role()));
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
        return new PlayerResponse(document.getId(), document.getName(), document.getLogin(),
                roleOf(document.getRole()));
    }

    /**
     * Absent role reads as {@link PlayerRole#PLAYER}, in both directions. Documents written
     * before the field existed are backfilled by changelog 008, but a request may still legally
     * omit it, so the default lives here rather than only in the migration.
     */
    private static PlayerRole roleOf(PlayerRole role) {
        return role == null ? PlayerRole.PLAYER : role;
    }
}
