package org.aventyrs.api.monster;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.aventyrs.api.monster.dto.MonsterSheetCreateRequest;
import org.aventyrs.api.monster.dto.MonsterSheetResponse;
import org.aventyrs.api.monster.dto.MonsterSheetUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monster-sheets")
@Tag(name = "MonsterSheets")
public class MonsterSheetController {

    private final MonsterSheetService service;

    public MonsterSheetController(MonsterSheetService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MonsterSheetResponse> create(@Valid @RequestBody MonsterSheetCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    /**
     * The Bestiário: authored foes only, no summons and no conjured ones (core 0.1.7.5). {@code campaign} is {@code
     * all} (the default), {@code lore}, or a Campanha id.
     */
    @GetMapping("/bestiary")
    public List<MonsterSheetResponse> bestiary(@RequestParam(required = false) String campaign) {
        return service.listBestiary(campaign);
    }

    /**
     * The Bestiário foes a Cena's picker offers: campaignId's and the lore ones, none fallen, none standing in a Cena
     * other than sceneId (core 0.1.7.5).
     */
    @GetMapping("/placeable")
    public List<MonsterSheetResponse> placeable(@RequestParam(required = false) String campaignId,
            @RequestParam(required = false) String sceneId) {
        return service.listPlaceable(campaignId, sceneId);
    }

    /** A conjurable monster as it would come out, never stored (core 0.1.7.5). */
    @PostMapping("/generate-preview")
    public MonsterSheetResponse generatePreview(@Valid @RequestBody org.aventyrs.api.monster.dto.ConjurePreviewRequest request) {
        return service.preview(request);
    }

    @GetMapping("/{id}")
    public MonsterSheetResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping
    public List<MonsterSheetResponse> list(@RequestParam(required = false) String playerId) {
        return playerId == null ? service.list() : service.listByPlayer(playerId);
    }

    @PutMapping("/{id}")
    public MonsterSheetResponse update(@PathVariable String id, @Valid @RequestBody MonsterSheetUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
