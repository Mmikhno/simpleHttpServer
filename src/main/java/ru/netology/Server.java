package ru.netology;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static BufferedReader in;
    private static BufferedOutputStream out;
    private final static int PORT = 9999;
    private static ServerSocket serverSocket;
    private static final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
    private ExecutorService executorService;

    public void serverStart() throws IOException {
        serverSocket = new ServerSocket(PORT);
        System.out.println("Server Started");
        executorService = Executors.newFixedThreadPool(64);
        while (true) {
            var socket = serverSocket.accept();
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new BufferedOutputStream(socket.getOutputStream());
                executorService.execute(new ProcessRequest());
            } catch (IOException e) {
                socket.close();
                System.out.println("Server disconnected");
                executorService.shutdown();
            }
        }
    }

    class ProcessRequest implements Runnable {
        private String requestLine;
        private String[] parts;

        @Override
        public void run() {
            try {
                requestLine = in.readLine();
                parts = requestLine.split(" ");
                if (parts.length != 3) {
                    return;
                }
                final var path = parts[1];
                if (!validPaths.contains(path)) {
                    out.write((
                            "HTTP/1.1 404 Not Found\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    out.flush();
                    return;
                }
                final var filePath = Path.of(".", "public", path);
                final var mimeType = Files.probeContentType(filePath);
                if (path.equals("/classic.html")) {
                    final var template = Files.readString(filePath);
                    final var content = template.replace(
                            "{time}",
                            LocalDateTime.now().toString()
                    ).getBytes();
                    out.write((
                            "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: " + mimeType + "\r\n" +
                                    "Content-Length: " + content.length + "\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    out.write(content);
                    out.flush();
                    return;
                }
                final var length = Files.size(filePath);
                out.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                Files.copy(filePath, out);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new Server().serverStart();
    }
}