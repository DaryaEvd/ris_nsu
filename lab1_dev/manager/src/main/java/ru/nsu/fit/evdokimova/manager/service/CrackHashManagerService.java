package ru.nsu.fit.evdokimova.manager.service;

import lombok.RequiredArgsConstructor;
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
    private final TaskDistributorService taskDistributorService;
    private final RestTemplate restTemplate;

    private static final String WORKER_URL = "http://worker:8081/internal/api/worker/hash/crack/task";

    private final Map<String, CrackRequestData> requestStorage = new ConcurrentHashMap<>();
    private final Queue<RequestFromManagerToWorker> taskQueue = new ConcurrentLinkedQueue<>();

    public ResponseForCrackToClient createCrackRequest(RequestForCrackFromClient request) {
        String requestId = UUID.randomUUID().toString();
        requestStorage.put(requestId, new CrackRequestData(StatusWork.IN_PROGRESS, new ArrayList<>(), System.currentTimeMillis()));

        int totalPermutations = taskDistributorService.calculateTotalPermutations(request.getMaxLength());
        int partCount = taskDistributorService.determinePartCount(totalPermutations);
        List<RequestFromManagerToWorker> tasks = taskDistributorService.divideTask(requestId, request.getHash(), request.getMaxLength(), totalPermutations, partCount);

        taskQueue.addAll(tasks);

        return new ResponseForCrackToClient(requestId);
    }

    public ResponseRequestIdToClient getCrackStatus(String requestId) {
        CrackRequestData requestData = requestStorage.get(requestId);
        if (requestData == null) {
            return new ResponseRequestIdToClient(StatusWork.ERROR, null);
        }
        return new ResponseRequestIdToClient(requestData.getStatus(), (ArrayList<String>) requestData.getData());
    }

    @Scheduled(fixedRate = 5000) // Каждые 5 секунд
    private void assignTasksToWorkers() {
        while (!taskQueue.isEmpty()) {
            RequestFromManagerToWorker task = taskQueue.poll();
            if (task != null) {
                sendTaskToWorker(task);
            }
        }
    }

    private void sendTaskToWorker(RequestFromManagerToWorker task) {
        try {
            restTemplate.postForEntity(WORKER_URL, task, Void.class);
        } catch (Exception e) {
            System.err.println("Ошибка отправки задачи воркеру: " + e.getMessage());
            taskQueue.add(task);
        }
    }

    public void processWorkerResponse(ResponseToManagerFromWorker response) {
        CrackRequestData requestData = requestStorage.get(response.getRequestId());
        if (requestData == null) return;

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
