package org.aventyrs.api.scene;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.api.scene.dto.SceneGroupResponse;
import org.aventyrs.api.scene.dto.SceneResponse;
import org.aventyrs.api.scene.dto.SceneUpdateRequest;
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
@RequestMapping("/api/scenes")
@Tag(name = "Scenes")
public class SceneController {

    private final SceneService service;

    public SceneController(SceneService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SceneResponse> create(@Valid @RequestBody SceneCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/available")
    public SceneResponse getAvailable() {
        return service.getAvailable();
    }

    @GetMapping("/{id}")
    public SceneResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping("/{id}/groups")
    public List<SceneGroupResponse> listGroups(@PathVariable String id) {
        return service.listGroups(id);
    }

    @GetMapping
    public List<SceneResponse> list() {
        return service.list();
    }

    @PutMapping("/{id}")
    public SceneResponse update(@PathVariable String id, @Valid @RequestBody SceneUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
