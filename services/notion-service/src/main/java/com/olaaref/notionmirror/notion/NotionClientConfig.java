package com.olaaref.notionmirror.notion;

import com.olaaref.notionmirror.NotionProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class NotionClientConfig {
    private static final Logger log = LoggerFactory.getLogger(NotionClientConfig.class);
    /** Set logging.level.com.olaaref.notionmirror.notion.http=DEBUG to see every request and response. */
    static final Logger HTTP = LoggerFactory.getLogger("com.olaaref.notionmirror.notion.http");
    private static final int MAX_LOGGED_CHARS = 4000;
    private static final Pattern SIGNATURE = Pattern.compile("(X-Amz-[A-Za-z-]+|Signature)=[^&\"\\s<>)]+");
    private static final long MIN_INTERVAL_MS = 400; // ~2.5 requests/second
    private static final int MAX_RETRIES = 3;

    private long lastCall = 0;

    @Bean
    NotionApi notionApi(NotionProperties props) {
        RestClient client = RestClient.builder()
                .baseUrl("https://api.notion.com")
                .requestFactory(new BufferingClientHttpRequestFactory(new JdkClientHttpRequestFactory()))
                .defaultHeader("Notion-Version", props.version())
                .requestInterceptor(authAndRateLimit(props))
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client))
                .build()
                .createClient(NotionApi.class);
    }

    private ClientHttpRequestInterceptor authAndRateLimit(NotionProperties props) {
        return (HttpRequest request, byte[] body, ClientHttpRequestExecution execution) -> {
            if (props.token() == null || props.token().isBlank()) {
                throw new IOException("NOTION_TOKEN is not set");
            }
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + props.token());
            for (int attempt = 0; ; attempt++) {
                throttle();
                long started = System.nanoTime();
                ClientHttpResponse response = execution.execute(request, body);
                if (HTTP.isDebugEnabled()) logExchange(request, body, response, (System.nanoTime() - started) / 1_000_000);
                if (response.getStatusCode().value() != 429 || attempt >= MAX_RETRIES) {
                    return response;
                }
                String retryAfter = response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
                response.close();
                long waitMs = retryAfter == null ? 1000 : (long) (Double.parseDouble(retryAfter) * 1000);
                log.warn("Notion rate limit hit, retrying in {} ms", waitMs);
                sleep(waitMs);
            }
        };
    }

    /** Logs one exchange. The Authorization header is never logged and signed file links are masked. */
    private static void logExchange(HttpRequest request, byte[] body, ClientHttpResponse response, long ms) {
        try {
            String responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
            HTTP.debug("Notion request: {} {}\n  body: {}\n", request.getMethod(), request.getURI(), clip(new String(body, StandardCharsets.UTF_8)));
            HTTP.debug("Notion response: {} in {} ms\n  body: {}",response.getStatusCode(), ms, clip(responseBody));
        } catch (IOException | RuntimeException e) {
            HTTP.debug("Notion exchange {} {} could not be logged: {}", request.getMethod(), request.getURI(), e.toString());
        }
    }

    static String clip(String s) {
        String masked = SIGNATURE.matcher(s).replaceAll("$1=***");
        return masked.length() <= MAX_LOGGED_CHARS ? masked
                : masked.substring(0, MAX_LOGGED_CHARS) + "... [" + (masked.length() - MAX_LOGGED_CHARS) + " more characters]";
    }

    private synchronized void throttle() {
        long wait = lastCall + MIN_INTERVAL_MS - System.currentTimeMillis();
        if (wait > 0) sleep(wait);
        lastCall = System.currentTimeMillis();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
