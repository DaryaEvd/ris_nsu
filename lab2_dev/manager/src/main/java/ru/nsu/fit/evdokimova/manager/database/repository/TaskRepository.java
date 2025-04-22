package ru.nsu.fit.evdokimova.manager.database.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.nsu.fit.evdokimova.manager.database.entity.TaskDocument;

import java.util.Optional;

public interface TaskRepository extends MongoRepository <TaskDocument, String> {
    Optional<TaskDocument> findByRequestId(String requestId);
}
