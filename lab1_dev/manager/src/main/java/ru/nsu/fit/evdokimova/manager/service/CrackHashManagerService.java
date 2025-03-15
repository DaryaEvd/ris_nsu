package ru.nsu.fit.evdokimova.manager.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
@RequiredArgsConstructor
public class CrackHashManagerService {

    private static final Logger log = LoggerFactory.getLogger(CrackHashManagerService.class);

    private final TaskDistributorService taskDistributorService;
    private final RestTemplate restTemplate;

    @Value("${worker.count}")
    private int workerCount;

    @Value("${worker.ports}")
    private String workerPorts;

    private List<String> workerUrls;
    private final Map<String, CrackRequestData> requestStorage = new ConcurrentHashMap<>();
    private final Queue<RequestFromManagerToWorker> taskQueue = new ConcurrentLinkedQueue<>();

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
        log.info("Новый запрос: hash={}, maxLength={}, requestId={}", request.getHash(), request.getMaxLength(), requestId);

        requestStorage.put(requestId, new CrackRequestData(StatusWork.IN_PROGRESS, new ArrayList<>(), System.currentTimeMillis()));

        int totalPermutations = taskDistributorService.calculateTotalPermutations(request.getMaxLength());
        int partCount = taskDistributorService.determinePartCount(totalPermutations, workerCount);
        log.info("Общее число перестановок: {}, частей: {}", totalPermutations, partCount);

        List<RequestFromManagerToWorker> tasks = taskDistributorService.divideTask(requestId, request.getHash(), request.getMaxLength(), totalPermutations, partCount);
        log.info("Запрос {} разбит на {} частей", requestId, tasks.size());

        assignTasksToWorkers(tasks);

        return new ResponseForCrackToClient(requestId);
    }

    private void assignTasksToWorkers(List<RequestFromManagerToWorker> tasks) {
//        int workerIndex = 0;
//        for (RequestFromManagerToWorker task : tasks) {
//            String workerUrl = workerUrls.get(workerIndex);
//            sendTaskToWorker(task, workerUrl);
//            workerIndex = (workerIndex + 1) % workerUrls.size();
//        }
        for (int i = 0; i < tasks.size(); i++) {
            String workerUrl = workerUrls.get(i % workerUrls.size());
            sendTaskToWorker(tasks.get(i), workerUrl);
        }
    }

    private void sendTaskToWorker(RequestFromManagerToWorker task, String workerUrl) {
        try {
            log.info("Отправка задачи воркеру {}: requestId={}, partNumber={}", workerUrl, task.getRequestId(), task.getPartNumber());
            restTemplate.postForEntity(workerUrl, task, Void.class);
        } catch (Exception e) {
            log.error("Ошибка отправки задачи воркеру {}: {}", workerUrl, e.getMessage());
            taskQueue.add(task);
        }
    }

    public ResponseRequestIdToClient getCrackStatus(String requestId) {
        CrackRequestData requestData = requestStorage.get(requestId);
        if (requestData == null) {
            log.warn("Запрос {} не найден в хранилище", requestId);
            return new ResponseRequestIdToClient(StatusWork.ERROR, null);
        }

        log.info("Запрос {}: статус={}, найденные слова={}", requestId, requestData.getStatus(), requestData.getData());
        return new ResponseRequestIdToClient(requestData.getStatus(), new ArrayList<>(requestData.getData()));
    }

    @Scheduled(fixedRate = 5000)
    private void retryFailedTasks() {
        if (taskQueue.isEmpty()) return;
        log.info("Повторная отправка {} задач", taskQueue.size());
        assignTasksToWorkers(new ArrayList<>(taskQueue));
        taskQueue.clear();
    }

    public void processWorkerResponse(ResponseToManagerFromWorker response) {
        CrackRequestData requestData = requestStorage.get(response.getRequestId());
        if (requestData == null) return;

        log.info("Воркер вернул результат для requestId={} -> {}", response.getRequestId(), response.getData());
        requestData.getData().addAll(response.getData());

        if (taskQueue.isEmpty()) {
            requestData.setStatus(StatusWork.READY);
        }
    }

    @Scheduled(fixedRate = 10000)
    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        requestStorage.forEach((requestId, requestData) -> {
            if (requestData.getStatus() == StatusWork.IN_PROGRESS && (now - requestData.getTimestamp()) > 30000) {
                requestData.setStatus(StatusWork.ERROR);
            }
        });
    }
}
