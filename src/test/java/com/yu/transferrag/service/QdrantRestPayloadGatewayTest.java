package com.yu.transferrag.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QdrantRestPayloadGatewayTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void readsPointsFromNestedResult() throws Exception {
        QdrantRestPayloadGateway gateway = gatewayFor(List.of(
                "{\"result\":{\"points\":[{\"id\":42,\"payload\":{\"chunkId\":10}}]},\"status\":\"ok\"}"));

        List<QdrantPayloadGateway.QdrantPoint> points = gateway.scrollAllPoints();

        assertEquals(1, points.size());
        assertEquals(42, points.getFirst().id());
        assertEquals(10, points.getFirst().payload().get("chunkId"));
    }

    @Test
    void acceptsAnEmptyNestedPointsArray() throws Exception {
        QdrantRestPayloadGateway gateway = gatewayFor(List.of(
                "{\"result\":{\"points\":[]},\"status\":\"ok\"}"));

        assertTrue(gateway.scrollAllPoints().isEmpty());
    }

    @Test
    void followsNestedNextPageOffsetWithoutDroppingPoints() throws Exception {
        List<String> requestBodies = new ArrayList<>();
        QdrantRestPayloadGateway gateway = gatewayFor(List.of(
                "{\"result\":{\"points\":[{\"id\":\"first\",\"payload\":{}}],\"next_page_offset\":\"next-id\"}}",
                "{\"result\":{\"points\":[{\"id\":2,\"payload\":{}}],\"next_page_offset\":null}}"), requestBodies);

        List<QdrantPayloadGateway.QdrantPoint> points = gateway.scrollAllPoints();

        assertEquals(List.of("first", 2), points.stream().map(QdrantPayloadGateway.QdrantPoint::id).toList());
        assertEquals(2, requestBodies.size());
        assertTrue(requestBodies.get(1).contains("next-id"));
    }

    @Test
    void rejectsMissingResult() throws Exception {
        QdrantRestPayloadGateway gateway = gatewayFor(List.of("{\"status\":\"ok\"}"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, gateway::scrollAllPoints);

        assertTrue(exception.getMessage().contains("result"));
    }

    @Test
    void rejectsMissingPointsInsideResult() throws Exception {
        QdrantRestPayloadGateway gateway = gatewayFor(List.of("{\"result\":{},\"status\":\"ok\"}"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, gateway::scrollAllPoints);

        assertTrue(exception.getMessage().contains("points"));
    }

    private QdrantRestPayloadGateway gatewayFor(List<String> responses) throws IOException {
        return gatewayFor(responses, new ArrayList<>());
    }

    private QdrantRestPayloadGateway gatewayFor(List<String> responses, List<String> requestBodies) throws IOException {
        AtomicInteger responseIndex = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/collections/transfer_chunks/points/scroll", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int index = responseIndex.getAndIncrement();
            if (index >= responses.size()) {
                send(exchange, 500, "unexpected extra scroll request");
                return;
            }
            send(exchange, 200, responses.get(index));
        });
        server.start();
        RestClient client = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .build();
        return new QdrantRestPayloadGateway(client, "transfer_chunks");
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
