package ru.nsu.fit.evdokimova.manager.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nsu.fit.evdokimova.manager.model.ResponseToManagerFromWorker;
import ru.nsu.fit.evdokimova.manager.model.RequestForCrackFromClient;
import ru.nsu.fit.evdokimova.manager.model.ResponseForCrackToClient;
import ru.nsu.fit.evdokimova.manager.model.ResponseRequestIdToClient;
import ru.nsu.fit.evdokimova.manager.service.CrackHashManagerService;

@RestController
@RequiredArgsConstructor
public class HashCrackController {

    private final CrackHashManagerService managerService;

    @PostMapping("/api/hash/crack")
    public ResponseEntity<ResponseForCrackToClient> crackHash(@RequestBody RequestForCrackFromClient request) {
        ResponseForCrackToClient response = managerService.createCrackRequest(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/hash/status")
    public ResponseEntity<ResponseRequestIdToClient> getStatus(@RequestParam String requestId) {
        ResponseRequestIdToClient response = managerService.getCrackStatus(requestId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/internal/api/manager/hash/crack/request")
    public ResponseEntity<Void> receiveWorkerResponse(@RequestBody ResponseToManagerFromWorker response) {
        managerService.processWorkerResponse(response);
        return ResponseEntity.ok().build();
    }
}