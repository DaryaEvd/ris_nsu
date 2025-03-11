package ru.nsu.fit.evdokimova.manager.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ResponseToManagerFromWorker {
    private String requestId;
    private List<String> data;
}
