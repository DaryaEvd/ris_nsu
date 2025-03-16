package ru.nsu.fit.evdokimova.manager.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.nsu.fit.evdokimova.manager.model.CrackRequestData;
import ru.nsu.fit.evdokimova.manager.model.RequestFromManagerToWorker;
import ru.nsu.fit.evdokimova.manager.model.ResponseToManagerFromWorker;
import ru.nsu.fit.evdokimova.manager.model.dto.RequestForCrackFromClient;
import ru.nsu.fit.evdokimova.manager.model.dto.ResponseForCrackToClient;
import ru.nsu.fit.evdokimova.manager.model.StatusWork;
import ru.nsu.fit.evdokimova.manager.model.dto.ResponseRequestIdToClient;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class CrackHashManagerService {
    private static final Logger log = LoggerFactory.getLogger(CrackHashManagerService.class);

    private final TaskDistributorService taskDistributorService;
    private final RestTemplate restTemplate;

    private final AtomicInteger workerIndex = new AtomicInteger(0);

    @Value("${worker.count}")
    private int workerCount;

    @Value("${worker.ports}")
    private String workerPorts;

    private List<String> workerUrls;
    private final Map<String, CrackRequestData> requestStorage = new ConcurrentHashMap<>();
    private final Queue<RequestFromManagerToWorker> taskQueue = new ConcurrentLinkedQueue<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    @PostConstruct
    private void init() {
        workerUrls = new ArrayList<>();
        String[] ports = workerPorts.split(",");
        for (int i = 0; i < workerCount; i++) {
            workerUrls.add(String.format("http://crackhash-worker-%d:%s/internal/api/worker/hash/crack/task", i + 1, ports[i]));
        }
    }

    public ResponseForCrackToClient createCrackRequest(RequestForCrackFromClient request) {
        String requestId = UUID.randomUUID().toString();
        log.info("Новый запрос: hash={}, maxLength={}, requestId={}",
                request.getHash(), request.getMaxLength(), requestId);

        requestStorage.put(requestId, new CrackRequestData(StatusWork.IN_PROGRESS,
                new CopyOnWriteArrayList<>(), System.currentTimeMillis(), 0, 0));

        executorService.submit(() -> processCrackRequest(requestId, request));

        return new ResponseForCrackToClient(requestId);
    }

    private void processCrackRequest(String requestId, RequestForCrackFromClient request) {
        int totalPermutations = taskDistributorService.calculateTotalPermutations(request.getMaxLength());
        int partNumber = taskDistributorService.determinePartNumber(totalPermutations, workerCount);
        log.info("Общее число перестановок: {}, частей: {}", totalPermutations, partNumber);

        CrackRequestData requestData = requestStorage.get(requestId);
        if (requestData != null) {
            requestData.setExpectedParts(partNumber);
        } else {
            log.error("Ошибка: не найден requestData для requestId={}", requestId);
        }

        taskDistributorService.divideTask(
                requestId, request.getHash(), request.getMaxLength(), totalPermutations, partNumber,
                this::assignTaskToWorker
        );
    }

    private void assignTaskToWorker(RequestFromManagerToWorker task) {
        executorService.submit(() -> {
            int index = workerIndex.getAndUpdate(i -> (i + 1) % workerUrls.size());
            String workerUrl = workerUrls.get(index);
            sendTaskToWorker(task, workerUrl);
        });
    }

    private void sendTaskToWorker(RequestFromManagerToWorker task, String workerUrl) {
        try {
            log.info("Отправка задачи воркеру {}: requestId={}, partNumber={}", workerUrl, task.getRequestId(), task.getPartNumber());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<RequestFromManagerToWorker> entity = new HttpEntity<>(task, headers);

            ResponseEntity<Void> response = restTemplate.exchange(
                    workerUrl, HttpMethod.POST, entity, Void.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Ошибка отправки: код " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Ошибка отправки задачи воркеру {}: {}, задача будет переназначена", workerUrl, e.getMessage());
            taskQueue.add(task);
        }
    }

    public void processWorkerResponse(ResponseToManagerFromWorker response) {
        CrackRequestData requestData = requestStorage.get(response.getRequestId());
        if (requestData == null) return;

        log.info("Воркер вернул результат для requestId={} -> {}", response.getRequestId(), response.getData());
        requestData.getData().addAll(response.getData());

        synchronized (requestData) {
            requestData.incrementCompletedParts();
            log.info("Обработано {} / {} частей для requestId={}", requestData.getCompletedParts(), requestData.getExpectedParts(), response.getRequestId());

            if (requestData.getCompletedParts() >= requestData.getExpectedParts()) {
                requestData.setStatus(StatusWork.READY);
                log.info("Запрос {} завершён, статус: READY", response.getRequestId());
            }
        }
    }

    @Scheduled(fixedRate = 5000)
    private void retryFailedTasks() {
        if (taskQueue.isEmpty()) return;

        log.info("Повторная отправка {} задач", taskQueue.size());

        List<RequestFromManagerToWorker> tasksToRetry = new ArrayList<>();

        while (!taskQueue.isEmpty()) {
            tasksToRetry.add(taskQueue.poll());
        }

        tasksToRetry.forEach(this::assignTaskToWorker);
    }

    public ResponseRequestIdToClient getCrackStatus(String requestId) {
        CrackRequestData requestData = requestStorage.get(requestId);
        if (requestData == null) {
            log.warn("Запрос {} не найден в хранилище", requestId);
            return new ResponseRequestIdToClient(StatusWork.ERROR, null);
        }

        log.info("Запрос {}: статус={}, найденные слова={}",
                requestId, requestData.getStatus(), requestData.getData());
        return new ResponseRequestIdToClient(requestData.getStatus(), new ArrayList<>(requestData.getData()));
    }
}
