package org.aventyrs.api.scene;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SceneRepository extends MongoRepository<SceneDocument, String> {

    Optional<SceneDocument> findTopByOrderByCreatedAtDesc();
}
