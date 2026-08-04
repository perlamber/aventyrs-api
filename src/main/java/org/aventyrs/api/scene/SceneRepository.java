package org.aventyrs.api.scene;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SceneRepository extends MongoRepository<SceneDocument, String> {
}
