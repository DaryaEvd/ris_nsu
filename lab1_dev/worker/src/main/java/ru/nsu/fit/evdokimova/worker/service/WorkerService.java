package ru.nsu.fit.evdokimova.worker.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.nsu.fit.evdokimova.worker.model.dto.RequestFromManagerToWorker;
import org.paukov.combinatorics3.Generator;
import ru.nsu.fit.evdokimova.worker.model.dto.ResponseToManagerFromWorker;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import static ru.nsu.fit.evdokimova.worker.config.ConstantsWorker.ALPHABET;

@Service
@RequiredArgsConstructor
public class WorkerService {
    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final RestTemplate restTemplate;
    private static final String MANAGER_URL = "http://crackhash-manager:8080/internal/api/manager/hash/crack/request";

    public void processTask(RequestFromManagerToWorker request) {
        log.info("Получена задача от менеджера: requestId={}, partNumber={}", request.getRequestId(), request.getPartNumber());

        List<String> foundWords = new ArrayList<>();
        String targetHash = request.getHash();

        List<String> words = generateWords(request.getMaxLength(), request.getPartNumber(), request.getPartCount());

        log.info("Воркер сгенерировал {} слов", words.size());
        for (String word : words) {
            String calculatedHash = DigestUtils.md5Hex(word);
            log.info("Проверка слова: '{}' -> хеш: {}", word, calculatedHash);
            if (calculatedHash.equals(targetHash)) {
                log.info("Найдено совпадение! requestId={}, Слово: {}", request.getRequestId(), word);
                foundWords.add(word);
            }
        }

        if (!foundWords.isEmpty()) {
            sendResultToManager(request.getRequestId(), foundWords);
        }else {
            log.info("Совпадений не найдено.");
            sendResultToManager(request.getRequestId(), foundWords);
        }
    }

    private List<String> generateWords(int maxLength, int partNumber, int partCount) {
        List<String> words = Generator.permutation(ALPHABET.split(""))
                .withRepetitions(maxLength)
                .stream()
                .skip(partNumber * (ALPHABET.length() / partCount))
                .limit(ALPHABET.length() / partCount)
                .map(list -> String.join("", list))
                .toList();

        log.info("Воркер сгенерировал {} слов для partNumber={}", words.size(), partNumber);
        return words;
    }

    private void sendResultToManager(String requestId, List<String> words) {
//        ResponseToManagerFromWorker response = new ResponseToManagerFromWorker(requestId, words);
//        try {
//            log.info("Отправка результата менеджеру: requestId={}", requestId);
//            restTemplate.postForEntity(MANAGER_URL, response, Void.class);
//            log.info("Результат успешно отправлен менеджеру: requestId={}", requestId);
//        } catch (Exception e) {
//            log.error("Ошибка отправки результата менеджеру: {}", e.getMessage());
//        }

        ResponseToManagerFromWorker response = new ResponseToManagerFromWorker(requestId, words);
        try {
            log.info("Отправка результата менеджеру: requestId={}, data={}", requestId, words);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ResponseToManagerFromWorker> entity = new HttpEntity<>(response, headers);

            restTemplate.exchange(
                    "http://crackhash-manager:8080/internal/api/manager/hash/crack/request",
                    HttpMethod.PATCH,
                    entity,
                    Void.class
            );

            log.info("Результат успешно отправлен менеджеру: requestId={}", requestId);
        } catch (Exception e) {
            log.error("Ошибка отправки результата менеджеру: {}", e.getMessage());
        }
    }
}
