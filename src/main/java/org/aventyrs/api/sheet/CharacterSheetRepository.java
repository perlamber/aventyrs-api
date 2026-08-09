package org.aventyrs.api.sheet;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CharacterSheetRepository extends MongoRepository<CharacterSheetDocument, String> {

    List<CharacterSheetDocument> findByPlayerId(String playerId);
}
