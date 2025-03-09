package ru.nsu.fit.evdokimova.manager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class RequestForCrackFromClient {
    public String hash;
    public Integer maxLength;
}
