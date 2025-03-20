package ru.nsu.fit.evdokimova.worker.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.nsu.fit.evdokimova.worker.model.dto.RequestFromManagerToWorker;
import org.paukov.combinatorics3.Generator;
import ru.nsu.fit.evdokimova.worker.model.dto.ResponseToManagerFromWorker;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import static ru.nsu.fit.evdokimova.worker.config.ConstantsWorker.ALPHABET;

@Service
@RequiredArgsConstructor
public class WorkerService {
    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final RestTemplate restTemplate;
    private static final String MANAGER_URL = "http://crackhash-manager:8080/internal/api/manager/hash/crack/request";

    public void processTask(RequestFromManagerToWorker request) {
        log.info("Received task: requestId={}, partNumber={}, range: {}-{}",
                request.getRequestId(), request.getPartNumber(), request.getStartIndex(), request.getEndIndex());

        List<String> foundWords = new ArrayList<>();
        String targetHash = request.getHash();

        List<String> words = generateWords(request.getMaxLength(), request.getStartIndex(), request.getEndIndex());

        log.info("Worker generated {} words", words.size());
        for (String word : words) {
            String calculatedHash = DigestUtils.md5Hex(word);
            if (calculatedHash.equals(targetHash)) {
                log.info("! Match found: {}", word);
                foundWords.add(word);
            }
        }

        sendResultToManager(request.getRequestId(), foundWords);
    }

    private List<String> generateWords(int maxLength, int startIndex, int endIndex) {
        return IntStream.rangeClosed(1, maxLength)
                .mapToObj(length ->
                        Generator.permutation(ALPHABET.split(""))
                                .withRepetitions(length)
                                .stream()
                )
                .flatMap(Function.identity())
                .skip(startIndex)
                .limit(endIndex - startIndex + 1)
                .map(list -> String.join("", list))
                .toList();
    }

    private void sendResultToManager(String requestId, List<String> words) {
        ResponseToManagerFromWorker response = new ResponseToManagerFromWorker(requestId, words);
        try {
            log.info("Send result to manager: requestId={}, data={}", requestId, words);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ResponseToManagerFromWorker> entity = new HttpEntity<>(response, headers);

            restTemplate.exchange(MANAGER_URL, HttpMethod.PATCH, entity, Void.class);
            log.info("Result has sent to manager: requestId={}", requestId);
        } catch (Exception e) {
            log.error("Error with sending result to manager: {}", e.getMessage());
        }
    }
}