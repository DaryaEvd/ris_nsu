package ru.nsu.fit.evdokimova.manager.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.nsu.fit.evdokimova.manager.model.CrackRequestData;
import ru.nsu.fit.evdokimova.manager.model.TaskData;
import ru.nsu.fit.evdokimova.manager.model.dto.RequestForCrackFromClient;
import ru.nsu.fit.evdokimova.manager.model.dto.ResponseForCrackToClient;
import ru.nsu.fit.evdokimova.manager.model.StatusWork;
import ru.nsu.fit.evdokimova.manager.model.dto.ResponseRequestIdToClient;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static ru.nsu.fit.evdokimova.manager.config.Constants.ALPHABET;
import static ru.nsu.fit.evdokimova.manager.config.Constants.WORKER_TASK_URI;

@Service
@RequiredArgsConstructor
public class CrackHashManagerService {
    private final RestTemplate restTemplate;
    private static final String WORKER_URL = "http://worker-service/internal/api/worker/hash/crack/task";
    private static final int TIMEOUT_MS = 30000;
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final Map<String, CrackRequestData> requestStorage = new ConcurrentHashMap<>();
    private final Queue<TaskData> taskQueue = new ConcurrentLinkedQueue<>();

    public ResponseForCrackToClient createCrackRequest(RequestForCrackFromClient request) {
        String requestId = UUID.randomUUID().toString();
        requestStorage.put(requestId, new CrackRequestData(StatusWork.IN_PROGRESS, new ArrayList<>(), System.currentTimeMillis()));

        int totalPermutations = calculateTotalPermutations(request.maxLength());
        int partCount = determinePartCount(totalPermutations);
        List<TaskData> tasks = divideTask(requestId, request.hash(), request.maxLength(), totalPermutations, partCount);

        taskQueue.addAll(tasks);
        assignTasksToWorkers();

        return new ResponseForCrackToClient(requestId);
    }

    public ResponseRequestIdToClient getCrackStatus(String requestId) {
        CrackRequestData requestData = requestStorage.get(requestId);
        if (requestData == null) {
            return new ResponseRequestIdToClient(StatusWork.ERROR, null);
        }
        return new ResponseRequestIdToClient(requestData.getStatus(), (ArrayList<String>) requestData.getData());
    }

    private int calculateTotalPermutations(int maxLength) {
        int total = 0;
        int base = ALPHABET.length();
        for (int i = 1; i <= maxLength; i++) {
            total += Math.pow(base, i);
        }
        return total;
    }

    private int determinePartCount(int totalPermutations) {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int estimatedWorkers = Math.max(2, cpuCores / 2);

        int optimalParts = Math.min(totalPermutations, Math.max(estimatedWorkers, totalPermutations / 10_000));

        return Math.max(1, optimalParts);

    }

    private List<TaskData> divideTask(String requestId, String hash, int maxLength, int totalPermutations, int partCount) {
        List<TaskData> tasks = new ArrayList<>();

        int chunkSize = totalPermutations / partCount;
        int remainder = totalPermutations % partCount;

        int currentStart = 0;
        for (int i = 0; i < partCount; i++) {
            int currentEnd = currentStart + chunkSize - 1;

            if (i == partCount - 1) {
                currentEnd += remainder;
            }

            tasks.add(new TaskData(requestId, hash, maxLength, partCount, i, currentStart, currentEnd));
            currentStart = currentEnd + 1;
        }

        return tasks;
    }

    private void assignTasksToWorkers() {
        while (!taskQueue.isEmpty()) {
            TaskData task = taskQueue.poll();
            if (task != null) {
                sendTaskToWorker(task);
            }
        }
    }

    private void sendTaskToWorker(TaskData task) {
        Map<String, Object> workerRequest = new HashMap<>();
        workerRequest.put("requestId", task.requestId());
        workerRequest.put("hash", task.hash());
        workerRequest.put("maxLength", task.maxLength());
        workerRequest.put("partCount", task.partCount());
        workerRequest.put("partNumber", task.partNumber());

        restTemplate.postForEntity(WORKER_URL, workerRequest, Void.class);
    }

    public void processWorkerResponse(String requestId, List<String> words) {
        CrackRequestData requestData = requestStorage.get(requestId);
        if (requestData == null) return;

        requestData.getData().addAll(words);

        if (taskQueue.isEmpty()) {
            requestData.setStatus(StatusWork.READY);
        } else {
            assignTasksToWorkers();
        }
    }

    @Scheduled(fixedRate = 10000)
    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        requestStorage.forEach((requestId, requestData) -> {
            if (requestData.getStatus() == StatusWork.IN_PROGRESS && (now - requestData.getTimestamp()) > TIMEOUT_MS) {
                requestData.setStatus(StatusWork.ERROR);
            }
        });
    }
}
