package ru.nsu.fit.evdokimova.manager.database.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.nsu.fit.evdokimova.manager.model.RequestFromManagerToWorker;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PendingTask {
    private RequestFromManagerToWorker task;
    private Date createdAt;
}