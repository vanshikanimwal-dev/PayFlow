package com.payflow.common;

import org.springframework.stereotype.Component;

@Component
public class RequestHasher {

    private final Jsons jsons;

    public RequestHasher(Jsons jsons) {
        this.jsons = jsons;
    }

    public String hash(String method, String path, Object body) {
        return Hashes.sha256(method + "\n" + path + "\n" + jsons.canonical(body));
    }
}
