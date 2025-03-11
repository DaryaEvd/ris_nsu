package ru.nsu.fit.evdokimova.manager.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
        for (String port : ports) {
            workerUrls.add("http://worker:" + port + "/internal/api/worker/hash/crack/task");
        }
    }


    public ResponseForCrackToClient createCrackRequest(RequestForCrackFromClient request) {
        String requestId = UUID.randomUUID().toString();
        requestStorage.put(requestId, new CrackRequestData(StatusWork.IN_PROGRESS, new ArrayList<>(), System.currentTimeMillis()));
        //todo: оптимизировать эту штуку
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


    @Scheduled(fixedRate = 5000)
    private void assignTasksToWorkers() {
        int workerIndex = 0;
        while (!taskQueue.isEmpty()) {
            RequestFromManagerToWorker task = taskQueue.poll();
            if (task != null) {
                String workerUrl = workerUrls.get(workerIndex);
                sendTaskToWorker(task, workerUrl);
                workerIndex = (workerIndex + 1) % workerUrls.size();  // Переключаемся на следующего воркера
            }
        }
    }


    private void sendTaskToWorker(RequestFromManagerToWorker task, String workerUrl) {
        try {
            restTemplate.postForEntity(workerUrl, task, Void.class);
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
