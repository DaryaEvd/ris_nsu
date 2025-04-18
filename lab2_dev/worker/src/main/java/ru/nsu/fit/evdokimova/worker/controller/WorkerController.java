package ru.nsu.fit.evdokimova.worker.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.nsu.fit.evdokimova.worker.service.WorkerService;
import ru.nsu.fit.evdokimova.worker.model.dto.RequestFromManagerToWorker;

//@RestController
//@RequiredArgsConstructor
//@RequestMapping("/internal/api/worker/hash/crack")
//public class WorkerController {
//
//    private final WorkerService workerService;
//
//    @PostMapping("/task")
//    public ResponseEntity<Void> handleTask(@RequestBody RequestFromManagerToWorker request) {
//        workerService.processTask(request);
//        return ResponseEntity.ok().build();
//    }
//}