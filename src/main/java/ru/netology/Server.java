package ru.netology;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLOutput;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final static int PORT = 9999;
    private static ServerSocket serverSocket;
    private ExecutorService executorService;
    private static ConcurrentHashMap<String, Handler> handlers = new ConcurrentHashMap<>();

    public Server() {
    }

    public void startServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Server has started");
        executorService = Executors.newFixedThreadPool(64);
        while (true) {
            try {
                var socket = serverSocket.accept();
                executorService.execute(new ClientServer(socket));
            } catch (IOException e) {
                System.out.println("Failed to connect");
                executorService.shutdown();
            }
        }
    }

    private static String getKeyValue(String method, String path) {
        StringBuilder sb = new StringBuilder();
        return sb.append(method).append(" ").append(path).toString();
    }

    private static Path getPath(Request request, String path) {
        final var requestPath = request.getPath();
        return Path.of(".", path, requestPath);
    }

    public static void main(String[] args) {
        try {
            createHandler();
            new Server().startServer(PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void addHandler(String method, String path, Handler handler) {
        String key = getKeyValue(method, path);
        handlers.put(key.toString(), handler);
    }

    private static void createHandler() throws IOException {
        addHandler("GET", "/index.html", new Handler() {
            public void handle(Request request, BufferedOutputStream responseStream) {
                try {
                    final var filePath = getPath(request, "/public");
                    final var mimeType = Files.probeContentType(filePath);
                    final var length = Files.size(filePath);
                    responseStream.write((
                            "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: " + mimeType + "\r\n" +
                                    "Content-Length: " + length + "\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    Files.copy(filePath, responseStream);
                    responseStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });
        addHandler("GET", "/classic.html", new Handler() {
            public void handle(Request request, BufferedOutputStream responseStream) {
                try {
                    final var filePath = getPath(request, "/public");
                    final var template = Files.readString(filePath);
                    final var mimeType = Files.probeContentType(filePath);
                    final var content = template.replace(
                            "{time}",
                            LocalDateTime.now().toString()
                    ).getBytes();
                    responseStream.write((
                            "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: " + mimeType + "\r\n" +
                                    "Content-Length: " + content.length + "\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    responseStream.write(content);
                    responseStream.flush();
                } catch (IOException e) {
                }
            }
        });
    }

    public static ConcurrentHashMap<String, Handler> getHandlers() {
        return handlers;
    }

    class ClientServer implements Runnable {
        private Socket socket;
        private static BufferedReader in;
        private static BufferedOutputStream out;

        public ClientServer(Socket socket) {
            this.socket = socket;
            try {
                in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
                out = new BufferedOutputStream(this.socket.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            Request req = createRequest();
            if (!getHandlers().containsKey(getKeyValue(req.getMethod(), req.getPath()))) {
                try {
                    out.write((
                            "HTTP/1.1 404 Not Found\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    out.flush();
                    return;
                } catch (IOException e) {
                }
            }
            chooseHandler(req);
        }

        private Request createRequest() {
            String[] parts;
            String path;
            String method;
            String requestLine;
            Request request = null;
            try {
                requestLine = in.readLine();
                System.out.println("request is " + requestLine);
                parts = requestLine.split(" ");
                if (parts.length != 3) {
                    return null;
                }
                path = parts[1];
                method = parts[0];
                request = new Request(method, path);
                return request;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        private void chooseHandler(Request request) {
            String key = getKeyValue(request.getMethod(), request.getPath());
            getHandlers().get(key).handle(request, out);
        }
    }


}

