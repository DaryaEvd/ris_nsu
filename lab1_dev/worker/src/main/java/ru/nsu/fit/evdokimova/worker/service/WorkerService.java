package ru.nsu.fit.evdokimova.worker.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.nsu.fit.evdokimova.worker.model.dto.RequestFromManagerToWorker;
import org.paukov.combinatorics3.Generator;
import ru.nsu.fit.evdokimova.worker.model.dto.ResponseToManagerFromWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkerService {

    private final RestTemplate restTemplate;
    private static final String MANAGER_URL = "http://manager:8080/internal/api/manager/hash/crack/request";

    public void processTask(WorkerTaskRequest request) {
        List<String> foundWords = new ArrayList<>();
        String targetHash = request.getHash();

        List<String> words = generateWords(request.getMaxLength(), request.getPartNumber(), request.getPartCount());

        for (String word : words) {
            String calculatedHash = DigestUtils.md5Hex(word);
            if (calculatedHash.equals(targetHash)) {
                foundWords.add(word);
            }
        }

        if (!foundWords.isEmpty()) {
            sendResultToManager(request.getRequestId(), foundWords);
        }
    }

    private List<String> generateWords(int maxLength, int partNumber, int partCount) {
        return Generator.permutation(ALPHABET.split(""))
                .withRepetitions(maxLength)
                .stream()
                .skip(partNumber * (ALPHABET.length() / partCount))
                .limit(ALPHABET.length() / partCount)
                .map(list -> list.stream().collect(Collectors.joining()))
                .toList();
    }

    private void sendResultToManager(String requestId, List<String> words) {
        ResponseToManagerFromWorker response = new ResponseToManagerFromWorker(requestId, words);
        restTemplate.patchForObject(MANAGER_URL, response, Void.class);
    }
}
