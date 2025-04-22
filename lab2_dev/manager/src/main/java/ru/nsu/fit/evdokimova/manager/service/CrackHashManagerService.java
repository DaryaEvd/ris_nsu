package ru.nsu.fit.evdokimova.manager.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.nsu.fit.evdokimova.manager.config.RabbitManagerConfig;
import ru.nsu.fit.evdokimova.manager.database.entity.PendingTaskDocument;
import ru.nsu.fit.evdokimova.manager.database.entity.TaskDocument;
import ru.nsu.fit.evdokimova.manager.database.repository.PendingTaskRepository;
import ru.nsu.fit.evdokimova.manager.database.repository.TaskRepository;
import ru.nsu.fit.evdokimova.manager.model.CrackRequestData;
import ru.nsu.fit.evdokimova.manager.model.RequestFromManagerToWorker;
import ru.nsu.fit.evdokimova.manager.model.ResponseToManagerFromWorker;
import ru.nsu.fit.evdokimova.manager.model.RequestForCrackFromClient;
import ru.nsu.fit.evdokimova.manager.model.ResponseForCrackToClient;
import ru.nsu.fit.evdokimova.manager.model.StatusWork;
import ru.nsu.fit.evdokimova.manager.model.ResponseRequestIdToClient;

import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class CrackHashManagerService {
    private static final Logger log = LoggerFactory.getLogger(CrackHashManagerService.class);

    private final TaskDistributorService taskDistributorService;
    private final RabbitTemplate rabbitTemplate;

    private final TaskRepository taskRepository;
    private final PendingTaskRepository pendingTaskRepository;

    @Value("${worker.count}")
    private int workerCount;

//    private final Map<String, CrackRequestData> requestStorage = new ConcurrentHashMap<>();
//    private final Queue<RequestFromManagerToWorker> taskQueue = new ConcurrentLinkedQueue<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public ResponseForCrackToClient createCrackRequest(RequestForCrackFromClient request) {
        String requestId = UUID.randomUUID().toString();
        log.info("New request: hash={}, maxLength={}, requestId={}",
                request.getHash(), request.getMaxLength(), requestId);

        TaskDocument taskDocument = new TaskDocument();
        taskDocument.setRequestId(requestId);
        taskDocument.setStatus(StatusWork.IN_PROGRESS);
        taskDocument.setData(new ArrayList<>());
        taskDocument.setCompletedParts(0);
        taskDocument.setHash(request.getHash());
        taskDocument.setMaxLength(request.getMaxLength());
        taskDocument.setSentToQueue(false);
        taskDocument.setPendingTasks(new ArrayList<>());

        taskDocument = taskRepository.save(taskDocument);
        log.info("Task saved to MongoDB: {}", taskDocument);

//        requestStorage.put(requestId, new CrackRequestData(StatusWork.IN_PROGRESS,
//                new CopyOnWriteArrayList<>(), 0, 0));

        executorService.submit(() -> processCrackRequest(requestId, request));

        return new ResponseForCrackToClient(requestId);
    }

    private void processCrackRequest(String requestId, RequestForCrackFromClient request) {
        int totalPermutations = taskDistributorService.calculateTotalPermutations(request.getMaxLength());
        int partNumber = taskDistributorService.determinePartNumber(totalPermutations, workerCount);
        log.info("Total permutations number: {}, parts: {}", totalPermutations, partNumber);

        taskRepository.findByRequestId(requestId).ifPresent(taskDocument -> {
            taskDocument.setExpectedParts(partNumber);
            taskRepository.save(taskDocument);
        });
//        CrackRequestData requestData = requestStorage.get(requestId);
//        if (requestData != null) {
//            requestData.setExpectedParts(partNumber);
//        } else {
//            log.error("Error: requestData not found for requestId={}", requestId);
//        }

        taskDistributorService.divideTask(
                requestId, request.getHash(), request.getMaxLength(), totalPermutations, partNumber,
                this::assignTaskToWorker
        );
    }

    private void assignTaskToWorker(RequestFromManagerToWorker task) {
        executorService.submit(() -> {
            try {
                log.info("Sending task to queue: requestId={}, part={}, range={}-{}",
                        task.getRequestId(), task.getPartNumber(),
                        task.getStartIndex(), task.getEndIndex());

                rabbitTemplate.convertAndSend(
                    RabbitManagerConfig.CRACK_HASH_EXCHANGE,
                    RabbitManagerConfig.TASKS_ROUTING_KEY,
                    task,
                    m -> {
                        m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return m;
                    }
                );

                taskRepository.findByRequestId(task.getRequestId()).ifPresent(taskDocument -> {
                    taskDocument.setSentToQueue(true);
                    taskRepository.save(taskDocument);
                });

            } catch (Exception e) {
                log.error("Error sending task to RabbitMQ: {}", e.getMessage());

                // Save failed task to MongoDB
                PendingTaskDocument pendingTask = new PendingTaskDocument();
                pendingTask.setTask(task);
                pendingTask.setCreatedAt(new Date());
                pendingTaskRepository.save(pendingTask);

                // Also add to the task document
                taskRepository.findByRequestId(task.getRequestId()).ifPresent(taskDocument -> {
                    taskDocument.getPendingTasks().add(task);
                    taskRepository.save(taskDocument);
                });
            }
        });
    }

    @RabbitListener(queues = RabbitManagerConfig.RESULTS_QUEUE)
    public void processWorkerResponse(ResponseToManagerFromWorker response) {
        Optional<TaskDocument> taskOpt = taskRepository.findByRequestId(response.getRequestId());
        if (taskOpt.isEmpty()) return;

        TaskDocument taskDocument = taskOpt.get();
        log.info("Worker sent result requestId={} -> {}", response.getRequestId(), response.getData());

        List<String> currentData = taskDocument.getData();
        if (currentData == null) {
            currentData = new ArrayList<>();
        }
        currentData.addAll(response.getData());
        taskDocument.setData(currentData);

        synchronized (taskDocument) {
            taskDocument.setCompletedParts(taskDocument.getCompletedParts() + 1);
            log.info("Processed {} / {} parts for requestId={}",
                    taskDocument.getCompletedParts(),
                    taskDocument.getExpectedParts(),
                    response.getRequestId());

            if (taskDocument.getCompletedParts() >= taskDocument.getExpectedParts()) {
                taskDocument.setStatus(StatusWork.READY);
                log.info("Request {} finished, status: READY", response.getRequestId());
            }

            taskRepository.save(taskDocument);
        }
    }

    @Scheduled(fixedRate = 5000)
    private void retryFailedTasks() {
        // Get pending tasks from MongoDB
        List<PendingTaskDocument> pendingTasks = pendingTaskRepository.findAllByOrderByCreatedAtAsc();
        if (pendingTasks.isEmpty()) return;

        log.info("Resending {} pending tasks", pendingTasks.size());

        for (PendingTaskDocument pendingTask : pendingTasks) {
            try {
                RequestFromManagerToWorker task = pendingTask.getTask();
                log.info("Resending task: requestId={}, part={}",
                        task.getRequestId(), task.getPartNumber());

                rabbitTemplate.convertAndSend(
                        RabbitManagerConfig.CRACK_HASH_EXCHANGE,
                        RabbitManagerConfig.TASKS_ROUTING_KEY,
                        task,
                        m -> {
                            m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                            return m;
                        }
                );

                // Remove from pending tasks if sent successfully
                pendingTaskRepository.delete(pendingTask);

                // Update main task document
                taskRepository.findByRequestId(task.getRequestId()).ifPresent(taskDocument -> {
                    taskDocument.getPendingTasks().removeIf(t ->
                            t.getPartNumber() == task.getPartNumber());
                    taskRepository.save(taskDocument);
                });
            } catch (Exception e) {
                log.error("Failed to resend task: {}", e.getMessage());
            }
        }
    }

    public ResponseRequestIdToClient getCrackStatus(String requestId) {
        Optional<TaskDocument> taskOpt = taskRepository.findByRequestId(requestId);
        if (taskOpt.isEmpty()) {
            log.warn("Request {} not found in storage", requestId);
            return new ResponseRequestIdToClient(StatusWork.ERROR, null);
        }

        TaskDocument taskDocument = taskOpt.get();
        log.info("Request {}: status={}, found words={}",
                requestId, taskDocument.getStatus(), taskDocument.getData());

        return new ResponseRequestIdToClient(
                taskDocument.getStatus(),
                new ArrayList<>(taskDocument.getData()));
    }

    /*
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
        log.info("New request: hash={}, maxLength={}, requestId={}",
                request.getHash(), request.getMaxLength(), requestId);

        requestStorage.put(requestId, new CrackRequestData(StatusWork.IN_PROGRESS,
                new CopyOnWriteArrayList<>(), 0, 0));

        executorService.submit(() -> processCrackRequest(requestId, request));

        return new ResponseForCrackToClient(requestId);
    }

    private void processCrackRequest(String requestId, RequestForCrackFromClient request) {
        int totalPermutations = taskDistributorService.calculateTotalPermutations(request.getMaxLength());
        int partNumber = taskDistributorService.determinePartNumber(totalPermutations, workerCount);
        log.info("Total permutations number: {}, parts: {}", totalPermutations, partNumber);

        CrackRequestData requestData = requestStorage.get(requestId);
        if (requestData != null) {
            requestData.setExpectedParts(partNumber);
        } else {
            log.error("Error: requestData not found for requestId={}", requestId);
        }

        int sum = 0;
        for (int i = 0; i < partNumber; i++) {
            int startIndex = i * (totalPermutations / partNumber);
            int endIndex = (i == partNumber - 1) ? totalPermutations - 1 : (startIndex + totalPermutations / partNumber - 1);
            log.info("Part {}: start={}, end={}, size={}", i, startIndex, endIndex, endIndex - startIndex + 1);
            sum += (endIndex - startIndex + 1);
        }

        if (sum != totalPermutations) {
            log.error("ERROR: Sum of parts ({}) != totalPermutations ({})", sum, totalPermutations);
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
            log.info("Sending task to worker {}: requestId={}, partNumber={}", workerUrl, task.getRequestId(), task.getPartNumber());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<RequestFromManagerToWorker> entity = new HttpEntity<>(task, headers);

            ResponseEntity<Void> response = restTemplate.exchange(
                    workerUrl, HttpMethod.POST, entity, Void.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Error with sending" + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error sending task to worker {}: {}, task will be reassigned", workerUrl, e.getMessage());
            taskQueue.add(task);
        }
    }

    public void processWorkerResponse(ResponseToManagerFromWorker response) {
        CrackRequestData requestData = requestStorage.get(response.getRequestId());
        if (requestData == null) return;

        log.info("Worker sent result requestId={} -> {}", response.getRequestId(), response.getData());
        requestData.getData().addAll(response.getData());

        synchronized (requestData) {
            requestData.incrementCompletedParts();
            log.info("Processed {} / {} parts for requestId={}", requestData.getCompletedParts(), requestData.getExpectedParts(), response.getRequestId());

            if (requestData.getCompletedParts() >= requestData.getExpectedParts()) {
                requestData.setStatus(StatusWork.READY);
                log.info("Request {} finished, status: READY", response.getRequestId());
            }
        }
    }

    @Scheduled(fixedRate = 5000)
    private void retryFailedTasks() {
        if (taskQueue.isEmpty()) return;

        log.info("Resend {} tasks", taskQueue.size());

        List<RequestFromManagerToWorker> tasksToRetry = new ArrayList<>();

        while (!taskQueue.isEmpty()) {
            tasksToRetry.add(taskQueue.poll());
        }

        tasksToRetry.forEach(this::assignTaskToWorker);
    }

    public ResponseRequestIdToClient getCrackStatus(String requestId) {
        CrackRequestData requestData = requestStorage.get(requestId);
        if (requestData == null) {
            log.warn("Request {} not found in storage", requestId);
            return new ResponseRequestIdToClient(StatusWork.ERROR, null);
        }

        log.info("Request {}: status={}, found words={}",
                requestId, requestData.getStatus(), requestData.getData());
        return new ResponseRequestIdToClient(requestData.getStatus(), new ArrayList<>(requestData.getData()));
    }
     */
}