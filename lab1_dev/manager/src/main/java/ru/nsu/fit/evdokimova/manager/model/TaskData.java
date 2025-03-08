package ru.nsu.fit.evdokimova.manager.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;


public record TaskData(
        String requestId,
        String hash,
        int maxLength,
        int partCount,
        int partNumber,
        int startIndex,
        int endIndex
) {}


//@Getter
//@Setter
//@AllArgsConstructor
//public class TaskData {
//    String requestId;
//    String hash;
//    int maxLength;
//    int partCount;
//    int partNumber;
//    int startIndex;
//    int endIndex;
//}
