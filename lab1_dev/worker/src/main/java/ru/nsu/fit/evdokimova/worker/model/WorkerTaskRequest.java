package ru.nsu.fit.evdokimova.worker.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class WorkerTaskRequest {
    private String requestId;
    private String hash;
    private int maxLength;
    private int partCount;
    private int partNumber;
}
