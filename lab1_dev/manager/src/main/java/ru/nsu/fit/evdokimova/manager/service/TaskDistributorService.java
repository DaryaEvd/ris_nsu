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
        int partSize = Math.max(500, totalPermutations / (workerCount * 2));
        return Math.min(totalPermutations, partSize);
    }

    public int determinePartNumber(int totalPermutations, int workerCount) {
//        int basePartSize = Math.max(500, totalPermutations / (workerCount * 2));
//        return (int) Math.ceil((double) totalPermutations / basePartSize);

        int partSize = Math.max(totalPermutations / 500, workerCount * 2);
        return Math.min(totalPermutations, partSize);
    }

    public List<RequestFromManagerToWorker> divideTask(String requestId, String hash, int maxLength, int totalPermutations, int partNumber) {
        List<RequestFromManagerToWorker> tasks = new ArrayList<>();

        int partSize = totalPermutations / partNumber;
        int remainder = totalPermutations % partNumber;

        int currentStart = 0;
        for (int i = 0; i < partNumber; i++) {
            int currentEnd = currentStart + partSize - 1;
            if (i == partNumber - 1) {
                currentEnd += remainder;
            }

            tasks.add(new RequestFromManagerToWorker(requestId, hash, maxLength, partNumber, i, currentStart, currentEnd));
            currentStart = currentEnd + 1;
        }

        return tasks;
    }
}
