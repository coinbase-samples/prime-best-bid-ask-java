/*
 * Copyright 2025-present Coinbase Global, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.coinbase.prime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BestBidAskPrinter {
    private static final Logger logger = LoggerFactory.getLogger(BestBidAskPrinter.class);

    private static final String WS_URI       = "wss://ws-feed.prime.coinbase.com";
    private static final String CHANNEL      = "l2_data";
    private static final String[] PRODUCT_IDS = {"BTC-USD"};
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final long INITIAL_RECONNECT_DELAY = 1000;

    private static class Book {
        final TreeMap<Double, Double> bids = new TreeMap<>(Collections.reverseOrder());
        final TreeMap<Double, Double> asks = new TreeMap<>();
    }
    private final Map<String, Book> books = new HashMap<>();
    private WebSocketClient client;
    private int reconnectAttempts = 0;
    private volatile boolean shutdown = false;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    private static final ThreadLocal<ObjectMapper> MAPPER =
            ThreadLocal.withInitial(ObjectMapper::new);

    public static void main(String[] args) {
        BestBidAskPrinter printer = new BestBidAskPrinter();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received, closing application gracefully...");
            printer.shutdown();
        }));
        
        try {
            printer.start();
        } catch (Exception e) {
            logger.error("Application failed to start: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private void start() throws Exception {
        validateEnvironmentVariables();
        createClient();
        
        if (!client.connectBlocking(30, TimeUnit.SECONDS)) {
            throw new RuntimeException("Failed to connect to WebSocket within 30 seconds");
        }
        
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Application interrupted");
        }
    }
    
    private void validateEnvironmentVariables() {
        String[] requiredEnvVars = {"API_KEY", "SECRET_KEY", "PASSPHRASE", "SVC_ACCOUNTID"};
        List<String> missing = new ArrayList<>();
        
        for (String var : requiredEnvVars) {
            if (System.getenv(var) == null || System.getenv(var).trim().isEmpty()) {
                missing.add(var);
            }
        }
        
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required environment variables: " + String.join(", ", missing));
        }
    }
    
    public void shutdown() {
        if (shutdown) {
            return;
        }
        
        shutdown = true;
        logger.info("Shutting down WebSocket connection...");
        
        if (client != null && !client.isClosed()) {
            client.close();
        }
        
        shutdownLatch.countDown();
    }

    private void createClient() throws Exception {
        client = new WebSocketClient(new URI(WS_URI)) {
            @Override 
            public void onOpen(ServerHandshake hs) { 
                logger.info("WebSocket connected to {}", WS_URI);
                reconnectAttempts = 0;
                send(buildSubscribeMessage()); 
            }
            
            @Override 
            public void onMessage(String msg) { 
                handle(msg); 
            }
            
            @Override 
            public void onClose(int code, String reason, boolean remote) { 
                logger.warn("WebSocket closed: {} (code: {}, remote: {})", reason, code, remote);
                if (!shutdown && !remote && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnect();
                } else if (shutdown) {
                    logger.info("WebSocket closed due to shutdown");
                }
            }
            
            @Override 
            public void onError(Exception ex) { 
                logger.error("WebSocket error: {}", ex.getMessage(), ex);
                if (!shutdown && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnect();
                }
            }
        };
    }

    private void reconnect() {
        if (shutdown) {
            return;
        }
        
        try {
            reconnectAttempts++;
            long delay = Math.min(INITIAL_RECONNECT_DELAY * (1L << Math.min(reconnectAttempts - 1, 6)), 30000);
            logger.info("Reconnecting in {} ms (attempt {}/{})", delay, reconnectAttempts, MAX_RECONNECT_ATTEMPTS);
            
            Thread.sleep(delay);
            
            if (shutdown) {
                return;
            }
            
            if (client != null && !client.isClosed()) {
                client.close();
            }
            
            createClient();
            if (!client.connectBlocking(30, TimeUnit.SECONDS)) {
                throw new RuntimeException("Failed to reconnect within 30 seconds");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Reconnection interrupted");
        } catch (Exception e) {
            logger.error("Reconnection failed: {}", e.getMessage(), e);
            if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                logger.error("Max reconnection attempts reached. Shutting down.");
                shutdown();
            }
        }
    }

    /* ---------- message handling ---------- */
    private void handle(String raw) {
        try {
            JsonNode root = MAPPER.get().readTree(raw);
            if (!CHANNEL.equals(root.path("channel").asText())) return;

            JsonNode events = root.path("events");
            if (!events.isArray() || events.isEmpty()) return;

            JsonNode evt  = events.get(0);
            String type    = evt.path("type").asText();
            String product = evt.path("product_id").asText();
            if (product.isEmpty()) return;

            JsonNode updates = evt.path("updates");
            if (!updates.isArray()) return;

            books.computeIfAbsent(product, p -> new Book());
            Book book = books.get(product);

            if ("snapshot".equals(type)) { book.bids.clear(); book.asks.clear(); }
            applyUpdates(updates, book);
            printBBA(product, book);

        } catch (Exception ex) {
            logger.error("Message parsing error: {}", ex.getMessage(), ex);
        }
    }

    private void applyUpdates(JsonNode updates, Book book) {
        for (JsonNode u : updates) {
            String side = u.path("side").asText();
            double px   = u.path("px").asDouble();
            double qty  = u.path("qty").asDouble();

            TreeMap<Double, Double> depth = "bid".equals(side) ? book.bids : book.asks;
            if (qty == 0.0) depth.remove(px); else depth.put(px, qty);
        }
    }

    private void printBBA(String product, Book book) {
        Map.Entry<Double, Double> bid = book.bids.firstEntry();
        Map.Entry<Double, Double> ask = book.asks.firstEntry();
        if (bid != null && ask != null) {
            logger.info("{} → Best Bid: {} (qty {}) | Best Ask: {} (qty {})",
                    product, 
                    String.format("%.8f", bid.getKey()), 
                    String.format("%.6f", bid.getValue()), 
                    String.format("%.8f", ask.getKey()), 
                    String.format("%.6f", ask.getValue()));
        }
    }

    private String buildSubscribeMessage() {
        long currentTimeMillis = System.currentTimeMillis();
        String ts = String.valueOf(currentTimeMillis / 1000);
        String key = env("API_KEY"), sec = env("SECRET_KEY"),
                pas = env("PASSPHRASE"), acct = env("SVC_ACCOUNTID");

        String sig = sign(CHANNEL, key, sec, acct, ts, PRODUCT_IDS);
        String prods = String.join("\",\"", PRODUCT_IDS);

        return String.format(
                "{"
                        + "\"type\":\"subscribe\","
                        + "\"channel\":\"%s\","
                        + "\"access_key\":\"%s\","
                        + "\"api_key_id\":\"%s\","
                        + "\"timestamp\":\"%s\","
                        + "\"passphrase\":\"%s\","
                        + "\"signature\":\"%s\","
                        + "\"product_ids\":[\"%s\"]"
                        + "}",
                CHANNEL, key, acct, ts, pas, sig, prods
        );
    }

    private static String sign(String ch, String key, String secret,
                               String acct, String ts, String[] prods) {
        String joined = String.join("", prods);
        String msg    = ch + key + acct + ts + joined;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder()
                    .encodeToString(mac.doFinal(msg.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) { throw new RuntimeException(e); }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing or empty environment variable: " + name);
        }
        return value.trim();
    }
}