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
import static ru.nsu.fit.evdokimova.worker.config.ConstantsWorker.ALPHABET;

@Service
@RequiredArgsConstructor
public class WorkerService {
    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final RestTemplate restTemplate;
    private static final String MANAGER_URL = "http://crackhash-manager:8080/internal/api/manager/hash/crack/request";

    public void processTask(RequestFromManagerToWorker request) {
        log.info("Получена задача: requestId={}, partNumber={}, диапазон: {}-{}",
                request.getRequestId(), request.getPartNumber(), request.getStartIndex(), request.getEndIndex());

        List<String> foundWords = new ArrayList<>();
        String targetHash = request.getHash();

        List<String> words = generateWords(request.getMaxLength(), request.getStartIndex(), request.getEndIndex());

        log.info("Воркер сгенерировал {} слов", words.size());
        for (String word : words) {
            String calculatedHash = DigestUtils.md5Hex(word);
            if (calculatedHash.equals(targetHash)) {
                log.info("Найдено совпадение: {}", word);
                foundWords.add(word);
            }
        }

        sendResultToManager(request.getRequestId(), foundWords);
    }

    private List<String> generateWords(int maxLength, int startIndex, int endIndex) {
        return Generator.permutation(ALPHABET.split(""))
                .withRepetitions(maxLength)
                .stream()
                .skip(startIndex)
                .limit(endIndex - startIndex + 1)
                .map(list -> String.join("", list))
                .toList();
    }

    private void sendResultToManager(String requestId, List<String> words) {
        ResponseToManagerFromWorker response = new ResponseToManagerFromWorker(requestId, words);
        try {
            log.info("Отправка результата менеджеру: requestId={}, data={}", requestId, words);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ResponseToManagerFromWorker> entity = new HttpEntity<>(response, headers);

            restTemplate.exchange(MANAGER_URL, HttpMethod.PATCH, entity, Void.class);
            log.info("Результат отправлен менеджеру: requestId={}", requestId);
        } catch (Exception e) {
            log.error("Ошибка отправки результата менеджеру: {}", e.getMessage());
        }
    }
}

/*
@Service
@RequiredArgsConstructor
public class WorkerService {
    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final RestTemplate restTemplate;
    private static final String MANAGER_URL = "http://crackhash-manager:8080/internal/api/manager/hash/crack/request";

    public void processTask(RequestFromManagerToWorker request) {
        log.info("Получена задача: requestId={}, partNumber={}, диапазон: {}-{}",
                request.getRequestId(), request.getPartNumber(), request.getStartIndex(), request.getEndIndex());

        List<String> foundWords = new ArrayList<>();
        String targetHash = request.getHash();

        List<String> words = generateWords(request.getMaxLength(), request.getStartIndex(), request.getEndIndex());

        log.info("Воркер сгенерировал {} слов", words.size());
        for (String word : words) {
            String calculatedHash = DigestUtils.md5Hex(word);
            if (calculatedHash.equals(targetHash)) {
                log.info("Найдено совпадение: {}", word);
                foundWords.add(word);
            }
        }

        sendResultToManager(request.getRequestId(), foundWords);
    }

    private List<String> generateWords(int maxLength, int startIndex, int endIndex) {
        return Generator.permutation(ALPHABET.split(""))
                .withRepetitions(maxLength)
                .stream()
                .skip(startIndex)
                .limit(endIndex - startIndex + 1)
                .map(list -> String.join("", list))
                .toList();
    }

    private void sendResultToManager(String requestId, List<String> words) {
        ResponseToManagerFromWorker response = new ResponseToManagerFromWorker(requestId, words);
        try {
            log.info("Отправка результата менеджеру: requestId={}, data={}", requestId, words);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ResponseToManagerFromWorker> entity = new HttpEntity<>(response, headers);

            restTemplate.exchange(MANAGER_URL, HttpMethod.PATCH, entity, Void.class);
            log.info("Результат отправлен менеджеру: requestId={}", requestId);
        } catch (Exception e) {
            log.error("Ошибка отправки результата менеджеру: {}", e.getMessage());
        }
    }
}
*/