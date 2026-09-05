package org.aventyrs.api.item;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ItemRepository extends MongoRepository<ItemDocument, String> {

    List<ItemDocument> findByProducedByCharacterId(String producedByCharacterId);
}
