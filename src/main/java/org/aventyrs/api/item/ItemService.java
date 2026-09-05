package org.aventyrs.api.item;

import java.util.List;
import java.util.UUID;
import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.item.dto.ItemRequest;
import org.aventyrs.api.item.dto.ItemResponse;
import org.springframework.stereotype.Service;

@Service
public class ItemService {

    private final ItemRepository repository;

    public ItemService(ItemRepository repository) {
        this.repository = repository;
    }

    public ItemResponse create(ItemRequest request) {
        ItemDocument document = new ItemDocument();
        document.setId(UUID.randomUUID().toString());
        ItemMapper.copyOnto(document, request);
        return ItemMapper.toResponse(repository.save(document));
    }

    public ItemResponse get(String id) {
        return ItemMapper.toResponse(findOrThrow(id));
    }

    public List<ItemResponse> list() {
        return repository.findAll().stream().map(ItemMapper::toResponse).toList();
    }

    public List<ItemResponse> listByProducedByCharacterId(String producedByCharacterId) {
        return repository.findByProducedByCharacterId(producedByCharacterId).stream()
                .map(ItemMapper::toResponse)
                .toList();
    }

    public ItemResponse update(String id, ItemRequest request) {
        ItemDocument document = findOrThrow(id);
        ItemMapper.copyOnto(document, request);
        return ItemMapper.toResponse(repository.save(document));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Item not found: " + id);
        }
        repository.deleteById(id);
    }

    private ItemDocument findOrThrow(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Item not found: " + id));
    }
}
