package com.dawei;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.datasource.url=jdbc:h2:mem:mcp_sse_testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class McpSseEndpointTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void sseEndpointShouldBeExposed() {
        webTestClient.get()
                .uri("/sse")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    void messageEndpointShouldBeExposed() {
        webTestClient.post()
                .uri("/mcp/message")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
