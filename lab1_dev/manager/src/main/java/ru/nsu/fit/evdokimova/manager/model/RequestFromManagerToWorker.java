package ru.nsu.fit.evdokimova.manager.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class RequestFromManagerToWorker {
    String requestId;
    String hash;
    int maxLength;
    int partCount;
    int partNumber;
    int startIndex;
    int endIndex;
}