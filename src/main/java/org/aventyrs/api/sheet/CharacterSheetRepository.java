package org.aventyrs.api.sheet;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CharacterSheetRepository extends MongoRepository<CharacterSheetDocument, String> {
}
