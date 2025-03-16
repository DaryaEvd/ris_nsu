package ru.nsu.fit.evdokimova.manager.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.nsu.fit.evdokimova.manager.model.RequestFromManagerToWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static ru.nsu.fit.evdokimova.manager.config.Constants.ALPHABET;

@RequiredArgsConstructor
@Service
public class TaskDistributorService {
    private static final Logger log = LoggerFactory.getLogger(TaskDistributorService.class);

    public int calculateTotalPermutations(int maxLength) {
        int total = 0;
        int lengthAlphabet = ALPHABET.length();
        for (int i = 1; i <= maxLength; i++) {
            total += (int) Math.pow(lengthAlphabet, i);
        }
        return total;
    }

    public int determinePartCount(int totalPermutations, int workerCount) {
        int partSize = Math.max(500, totalPermutations / (workerCount * 2));
        return Math.min(totalPermutations, partSize);
    }

    public int determinePartNumber(int totalPermutations, int workerCount) {
//        int basePartSize = Math.max(500, totalPermutations / (workerCount * 2));
//        return (int) Math.ceil((double) totalPermutations / basePartSize);

//        int partSize = Math.max(totalPermutations / 500, workerCount * 2);
//        return Math.min(totalPermutations, partSize);

        return 10_000;
    }

    public void divideTask(
            String requestId,
            String hash,
            int maxLength,
            int totalPermutations,
            int partNumber,
            Consumer<RequestFromManagerToWorker> taskConsumer
    ) {
        int partSize = totalPermutations / partNumber;
        int remainder = totalPermutations % partNumber;

        int currentStart = 0;
        for (int i = 0; i < partNumber; i++) {
            int currentEnd = currentStart + partSize - 1;
            if (i == partNumber - 1) {
                currentEnd += remainder;
            }

            RequestFromManagerToWorker task = new RequestFromManagerToWorker(requestId, hash, maxLength, partNumber, i, currentStart, currentEnd);
            log.info("Создана задача partNumber={} | start={} | end={}", i, currentStart, currentEnd);

            taskConsumer.accept(task);

            currentStart = currentEnd + 1;
        }
    }
}
