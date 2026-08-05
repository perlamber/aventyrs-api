package org.aventyrs.api.player;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.player.dto.PlayerResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
@Tag(name = "Players")
public class PlayerController {

    private final PlayerService service;

    public PlayerController(PlayerService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PlayerResponse> create(@Valid @RequestBody PlayerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{id}")
    public PlayerResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping("/by-login/{login}")
    public PlayerResponse getByLogin(@PathVariable String login) {
        return service.getByLogin(login);
    }

    @GetMapping
    public List<PlayerResponse> list() {
        return service.list();
    }

    @PutMapping("/{id}")
    public PlayerResponse update(@PathVariable String id, @Valid @RequestBody PlayerRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
