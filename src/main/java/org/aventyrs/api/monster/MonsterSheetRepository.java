package org.aventyrs.api.monster;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MonsterSheetRepository extends MongoRepository<MonsterSheetDocument, String> {

    List<MonsterSheetDocument> findByPlayerId(String playerId);
}
