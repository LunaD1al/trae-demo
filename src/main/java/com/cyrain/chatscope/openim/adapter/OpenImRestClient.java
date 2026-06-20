package com.cyrain.chatscope.openim.adapter;

import java.util.UUID;

import tools.jackson.databind.JsonNode;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin transport over the OpenIM REST API.
 *
 * <p>Every OpenIM response is wrapped as {@code {errCode, errMsg, data}}. This client performs the
 * POST, requires HTTP success, and raises {@link OpenImApiException} when {@code errCode != 0} so
 * callers only ever see the {@code data} node on success.
 */
@Component
public class OpenImRestClient {

    private final RestClient restClient;

    OpenImRestClient() {
        this.restClient = RestClient.create();
    }

    /**
     * POST {@code baseUrl + path} with an optional OpenIM {@code token} header and return the
     * {@code data} node of the OpenIM envelope.
     */
    JsonNode post(String baseUrl, String path, String token, Object body) {
        String url = baseUrl + path;
        JsonNode envelope;
        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("operationID", UUID.randomUUID().toString());
            if (token != null && !token.isBlank()) {
                spec = spec.header("token", token);
            }
            envelope = spec.body(body).retrieve().body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new OpenImApiException("OpenIM call failed: POST " + url, ex);
        }

        if (envelope == null) {
            throw new OpenImApiException("OpenIM returned an empty body: POST " + url, -1);
        }
        int errCode = envelope.path("errCode").asInt(-1);
        if (errCode != 0) {
            String errMsg = envelope.path("errMsg").asString("");
            throw new OpenImApiException(
                    "OpenIM call POST " + url + " returned errCode=" + errCode + " errMsg=" + errMsg, errCode);
        }
        return envelope.path("data");
    }
}
