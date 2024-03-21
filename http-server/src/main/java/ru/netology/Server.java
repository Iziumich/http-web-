package ru.netology;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final ExecutorService threadPool;
    private final Map<String, Map<String, Handler>> handlers;

    public Server() {
        this.threadPool = Executors.newFixedThreadPool(64);
        this.handlers = new HashMap<>();
    }

    public void addHandler(String method, String path, Handler handler) {
        if (!handlers.containsKey(method)) {
            handlers.put(method, new HashMap<>());
        }
        handlers.get(method).put(path, handler);
    }

    public void listen(int port) {
        try (final var serverSocket = new ServerSocket(port)) {
            while (true) {
                final var socket = serverSocket.accept();
                threadPool.submit(() -> handleConnection(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleConnection(Socket socket) {
        try (
                socket;
                final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                final var out = new BufferedOutputStream(socket.getOutputStream());
        ) {
            // read only request line for simplicity
            // must be in form GET /path HTTP/1.1
            final var requestLine = in.readLine();
            final var parts = requestLine.split(" ");

            if (parts.length != 3) {
                // just close socket
                return;
            }

            final var method = parts[0];
            final var path = parts[1];
            final var headers = readHeaders(in);
            final var body = readBody(in, headers);
            Map<String, String> params = null;
            if ("POST".equals(method) && "application/x-www-form-urlencoded".equals(headers.get("Content-Type"))) {
                params = parseQueryParams(body);
            }
            final var handlerMap = handlers.get(method);
            if (handlerMap == null || !handlerMap.containsKey(path)) {
                notFound(out);
                return;
            }

            final var handler = handlerMap.get(path);
            handler.handle(new Request(method, path, headers, body, params), out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
            String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : null;
            params.put(key, value);
        }
        return params;
    }
    private Map<String, String> readHeaders(BufferedReader in) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        while (!(line = in.readLine()).isEmpty()) {
            final var index = line.indexOf(":");
            if (index == -1) {
                throw new IOException("Invalid header format: " + line);
            }
            headers.put(line.substring(0, index).trim(), line.substring(index + 1).trim());
        }
        return headers;
    }

    private String readBody(BufferedReader in, Map<String, String> headers) throws IOException {
        if (!headers.containsKey("Content-Length")) {
            return null;
        }
        final var contentLength = Integer.parseInt(headers.get("Content-Length"));
        final var body = new char[contentLength];
        in.read(body, 0, contentLength);
        return new String(body);
    }

    private void notFound(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 404 Not Found\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }
}