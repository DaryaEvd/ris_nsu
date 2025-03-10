package ru.nsu.fit.evdokimova.manager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import ru.nsu.fit.evdokimova.manager.model.StatusWork;

import java.util.ArrayList;

@Setter
@Getter
@AllArgsConstructor
public class ResponseRequestIdToClient {
    StatusWork status;
    ArrayList<String> data;
}
