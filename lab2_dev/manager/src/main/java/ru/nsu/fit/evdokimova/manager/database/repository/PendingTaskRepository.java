package ru.nsu.fit.evdokimova.manager.database.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.nsu.fit.evdokimova.manager.database.entity.PendingTaskDocument;

import java.util.List;

public interface PendingTaskRepository extends MongoRepository<PendingTaskDocument, String> {
    List<PendingTaskDocument> findAllByOrderByCreatedAtAsc();
}