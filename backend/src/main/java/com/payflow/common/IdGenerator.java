package com.payflow.common;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdGenerator {

    public UUID newId() {
        return UUID.randomUUID();
    }
}
