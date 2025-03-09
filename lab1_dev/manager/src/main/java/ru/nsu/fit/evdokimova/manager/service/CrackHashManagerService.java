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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
@RequiredArgsConstructor
public class CrackHashManagerService {

    private final TaskDistributorService taskDistributorService;

    private final Map<String, CrackRequestData> requestStorage = new ConcurrentHashMap<>();
    private final Queue<TaskData> taskQueue = new ConcurrentLinkedQueue<>();

    public ResponseForCrackToClient createCrackRequest(RequestForCrackFromClient request) {
        String requestId = UUID.randomUUID().toString();
        requestStorage.put(requestId, new CrackRequestData(StatusWork.IN_PROGRESS, new ArrayList<>(), System.currentTimeMillis()));

        int totalPermutations = taskDistributorService.calculateTotalPermutations(request.getMaxLength());
        int partCount = taskDistributorService.determinePartCount(totalPermutations);
        List<TaskData> tasks = taskDistributorService.divideTask(requestId, request.getHash(), request.getMaxLength(), totalPermutations, partCount);

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
