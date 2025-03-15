package ru.nsu.fit.evdokimova.manager.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.nsu.fit.evdokimova.manager.model.RequestFromManagerToWorker;

import java.util.ArrayList;
import java.util.List;

import static ru.nsu.fit.evdokimova.manager.config.Constants.ALPHABET;

@RequiredArgsConstructor
@Service
public class TaskDistributorService {

    public int calculateTotalPermutations(int maxLength) {
        int total = 0;
        int lengthAlphabet = ALPHABET.length();
        for (int i = 1; i <= maxLength; i++) {
            total += (int) Math.pow(lengthAlphabet, i);
        }
        return total;
    }

    public int determinePartCount(int totalPermutations, int workerCount) {
//        int cpuCores = Runtime.getRuntime().availableProcessors();
//        int estimatedWorkers = Math.max(2, cpuCores / 2);
//
//        int optimalParts = Math.min(totalPermutations, Math.max(estimatedWorkers, totalPermutations / 500));
//
//        return Math.max(1, optimalParts);
        return Math.min(totalPermutations, workerCount);
    }

    public List<RequestFromManagerToWorker> divideTask(String requestId, String hash, int maxLength, int totalPermutations, int partCount) {
        List<RequestFromManagerToWorker> tasks = new ArrayList<>();

        int chunkSize = totalPermutations / partCount;
        int remainder = totalPermutations % partCount;

        int currentStart = 0;
        for (int i = 0; i < partCount; i++) {
            int currentEnd = currentStart + chunkSize - 1;

            if (i == partCount - 1) {
                currentEnd += remainder;
            }

            tasks.add(new RequestFromManagerToWorker(requestId, hash, maxLength, partCount, i, currentStart, currentEnd));
            currentStart = currentEnd + 1;
        }

        return tasks;
    }
}
