package ru.netology;

public class Main {
    public static void main(String[] args) {
        final var server = new Server();

        server.addHandler("GET", "/messages", (request, responseStream) -> {
            // TODO: handlers code for GET /messages
        });

        server.addHandler("POST", "/messages", (request, responseStream) -> {
            // TODO: handlers code for POST /messages
        });

        server.listen(9999);
    }
}