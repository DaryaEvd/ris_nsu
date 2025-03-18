package ru.nsu.fit.evdokimova.manager.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
@AllArgsConstructor
public class CrackRequestData {
    private StatusWork status;
    private List<String> data;
    private int expectedParts;

    private int completedParts;
    public synchronized void incrementCompletedParts() {
        this.completedParts++;
    }
}