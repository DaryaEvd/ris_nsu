package ru.nsu.fit.evdokimova.manager.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class RequestFromManagerToWorker {
    private String requestId;
    private String hash;
    private int maxLength;
    private int partCount;
    private int partNumber;
    private int startIndex;
    private int endIndex;
}