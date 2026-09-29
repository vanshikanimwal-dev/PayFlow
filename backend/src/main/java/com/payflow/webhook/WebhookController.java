package com.payflow.webhook;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final WebhookCoordinator webhooks;

    public WebhookController(WebhookCoordinator webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping("/gateway")
    public ResponseEntity<Void> gateway(
            @RequestHeader(value = "X-Gateway-Signature", required = false) String signature, HttpServletRequest request)
            throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        webhooks.handle(body, signature);
        return ResponseEntity.ok().build();
    }
}
