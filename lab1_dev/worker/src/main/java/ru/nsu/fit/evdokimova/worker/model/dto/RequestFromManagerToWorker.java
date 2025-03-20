package ru.nsu.fit.evdokimova.worker.model.dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestFromManagerToWorker {
    private String requestId;
    private String hash;
    private int maxLength;
    private int partCount;
    private int partNumber;
    private int startIndex;
    private int endIndex;
}