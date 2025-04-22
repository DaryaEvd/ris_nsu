package ru.nsu.fit.evdokimova.manager.database.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import ru.nsu.fit.evdokimova.manager.model.RequestFromManagerToWorker;

import java.util.Date;

@Document(collection = "pending_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PendingTaskDocument {
    @Id
    private String id;
    private RequestFromManagerToWorker task;
    private Date createdAt;
}
