package ru.nsu.fit.evdokimova.manager.database.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import ru.nsu.fit.evdokimova.manager.model.RequestFromManagerToWorker;
import ru.nsu.fit.evdokimova.manager.model.StatusWork;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Document(collection = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskDocument {
    @Id
    private String id;
    private String requestId;
    private StatusWork status;
    private List<String> data;
    private int expectedParts;
    private int completedParts;
    private String hash;
    private Integer maxLength;

    @Field("pending_tasks")
    private List<PendingTask> pendingTasks = new ArrayList<>();
}