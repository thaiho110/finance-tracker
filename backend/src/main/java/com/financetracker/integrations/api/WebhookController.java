package com.financetracker.integrations.api;

import com.financetracker.integrations.service.WebhookInboundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound webhooks (ADR-023 §3). No JWT — authenticated by the {@code token} in the path plus an HMAC-SHA256
 * signature over {@code "<X-Kept-Timestamp>.<body>"}. A valid call raises an internal event and notifies the owner.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks (inbound)", description = "External systems call these with an HMAC signature")
public class WebhookController {

    private final WebhookInboundService inbound;

    @PostMapping("/{token}")
    @Operation(summary = "Receive a signed inbound webhook and raise an internal event")
    public ResponseEntity<Void> receive(@PathVariable String token,
                                        @RequestHeader(value = "X-Kept-Timestamp", required = false) String timestamp,
                                        @RequestHeader(value = "X-Kept-Signature", required = false) String signature,
                                        @RequestBody(required = false) byte[] body) {
        inbound.handle(token, timestamp, signature, body);
        return ResponseEntity.accepted().build();
    }
}
