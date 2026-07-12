package com.orderprocessing.webui.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.webui.exception.BackendClientException;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.UUID;

@Configuration
public class ClientConfig {
    @Bean
    RestClient.Builder platformRestClientBuilder(WebUiProperties properties, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getServices().getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getServices().getReadTimeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    String id = MDC.get("correlationId");
                    request.getHeaders().set(CorrelationIdFilter.HEADER, id == null ? UUID.randomUUID().toString() : id);
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(status -> status.isError(), (request, response) -> {
                    Map<String, Object> payload = Map.of();
                    try {
                        payload = objectMapper.readValue(response.getBody(), new TypeReference<>() { });
                    } catch (Exception ignored) {
                        // The UI deliberately replaces non-contract backend bodies with a safe message.
                    }
                    String code = payload.get("code") instanceof String value ? value : "BACKEND_ERROR";
                    String message = payload.get("message") instanceof String value ? value : "The service could not complete this request";
                    Map<String, String> fields = Map.of();
                    Object rawFields = payload.get("fieldErrors");
                    if (rawFields instanceof Map<?, ?> rawMap) {
                        fields = rawMap.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                                entry -> String.valueOf(entry.getKey()), entry -> String.valueOf(entry.getValue())));
                    }
                    throw new BackendClientException(response.getStatusCode(), code, message, fields);
                });
    }
}
