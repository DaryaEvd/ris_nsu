package ru.nsu.fit.evdokimova.manager.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nsu.fit.evdokimova.manager.model.dto.RequestForCrackFromClient;
import ru.nsu.fit.evdokimova.manager.model.dto.ResponseForCrackToClient;
import ru.nsu.fit.evdokimova.manager.model.dto.ResponseRequestIdToClient;
import ru.nsu.fit.evdokimova.manager.service.CrackHashManagerService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hash")
public class HashCrackController {
    private final CrackHashManagerService managerService;

    @PostMapping("/crack")
    public ResponseEntity<ResponseForCrackToClient> crackHash(@RequestBody RequestForCrackFromClient request) {
        ResponseForCrackToClient response = managerService.createCrackRequest(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<ResponseRequestIdToClient> getStatus(@RequestParam String requestId) {
        ResponseRequestIdToClient response = managerService.getCrackStatus(requestId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/request")
    public ResponseEntity<Void> receiveWorkerResponse(@RequestBody ManagerResponse response) {
        managerService.processWorkerResponse(response);
        return ResponseEntity.ok().build();
    }
}