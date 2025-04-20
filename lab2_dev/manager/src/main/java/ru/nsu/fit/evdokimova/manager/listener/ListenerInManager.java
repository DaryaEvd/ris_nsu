package ru.nsu.fit.evdokimova.manager.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.nsu.fit.evdokimova.manager.config.RabbitManagerConfig;
import ru.nsu.fit.evdokimova.manager.model.ResponseToManagerFromWorker;
import ru.nsu.fit.evdokimova.manager.service.CrackHashManagerService;

@Component
@RequiredArgsConstructor
public class ListenerInManager {
    private final CrackHashManagerService managerService;

    @RabbitListener(queues = RabbitManagerConfig.RESULTS_QUEUE)
    public void receiveWorkerResponse(ResponseToManagerFromWorker response) {
        managerService.processWorkerResponse(response);
    }
}
