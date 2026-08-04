package org.aventyrs.api.sheet;

import jakarta.validation.Valid;
import java.util.List;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.CharacterSheetResponse;
import org.aventyrs.api.sheet.dto.CharacterSheetUpdateRequest;
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
@RequestMapping("/api/character-sheets")
public class CharacterSheetController {

    private final CharacterSheetService service;

    public CharacterSheetController(CharacterSheetService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CharacterSheetResponse> create(@Valid @RequestBody CharacterSheetCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{id}")
    public CharacterSheetResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping
    public List<CharacterSheetResponse> list() {
        return service.list();
    }

    @PutMapping("/{id}")
    public CharacterSheetResponse update(@PathVariable String id, @Valid @RequestBody CharacterSheetUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
