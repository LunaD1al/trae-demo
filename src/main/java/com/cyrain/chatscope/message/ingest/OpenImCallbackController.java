package com.cyrain.chatscope.message.ingest;

import java.util.Map;

import com.cyrain.chatscope.openim.adapter.OpenImProperties;
import tools.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenIM webhook receiver. OpenIM posts every enabled callback to this single URL and identifies the
 * event via {@code callbackCommand}.
 *
 * <p>Responses always use HTTP 200 with OpenIM's {@code {errCode, errMsg}} envelope. Ignored or
 * duplicate callbacks return success so OpenIM does not retry; only a genuinely unparseable payload
 * returns a non-zero {@code errCode}. The response keys are intentionally camelCase (OpenIM's wire
 * format), so a {@code Map} is returned rather than a snake_case-mapped record.
 */
@RestController
class OpenImCallbackController {

    private static final Logger log = LoggerFactory.getLogger(OpenImCallbackController.class);
    private static final String SECRET_HEADER = "X-ChatScope-Callback-Secret";

    private final MessageIngestService ingestService;
    private final OpenImProperties openImProperties;

    OpenImCallbackController(MessageIngestService ingestService, OpenImProperties openImProperties) {
        this.ingestService = ingestService;
        this.openImProperties = openImProperties;
    }

    @PostMapping(path = "/api/openim/callback", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> callback(
            @RequestBody JsonNode payload,
            @RequestParam(name = "secret", required = false) String secretParam,
            @RequestHeader(name = SECRET_HEADER, required = false) String secretHeader) {
        if (!secretMatches(secretParam, secretHeader)) {
            log.warn("Rejected OpenIM callback with invalid secret");
            return envelope(1, "invalid callback secret");
        }

        String command = payload.path("callbackCommand").asString();
        if (!CallbackNormalizer.AFTER_SEND_GROUP_MSG.equals(command)) {
            // Other (or absent) callback commands are not handled here; ack so OpenIM does not retry.
            return success();
        }

        try {
            MessageIngestService.IngestOutcome outcome = ingestService.ingestGroupMessage(payload);
            log.debug("Ingest outcome={} for command={}", outcome, command);
            return success();
        } catch (IllegalArgumentException ex) {
            log.warn("Failed to parse OpenIM group-message callback: {}", ex.getMessage());
            return envelope(1, "callback parse failed");
        }
    }

    private boolean secretMatches(String secretParam, String secretHeader) {
        String configured = openImProperties.callbackSecret();
        if (configured == null || configured.isBlank()) {
            return true;
        }
        return configured.equals(secretParam) || configured.equals(secretHeader);
    }

    private static Map<String, Object> success() {
        return envelope(0, "");
    }

    private static Map<String, Object> envelope(int errCode, String errMsg) {
        return Map.of("actionCode", 0, "errCode", errCode, "errMsg", errMsg, "nextCode", 0);
    }
}
