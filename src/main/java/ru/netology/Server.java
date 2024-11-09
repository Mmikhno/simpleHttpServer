package ru.netology;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLOutput;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
        addHandler("POST", "/message", new Handler() {
            @Override
            public void handle(Request request, BufferedOutputStream responseStream) {
                try {
                    if (request.getHeaderByName("Content-Type").contains("application/x-www-form-urlencoded")) {
                        String postParams = request.getPostParamsAsString();
                        String response = "HTTP/1.1 200 OK\r\n" +
                                "Content-Length: 0" + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n";
                        responseStream.write(response.getBytes());
                        responseStream.flush();
                        System.out.println(request.getHeadersAsString());
                        System.out.println(postParams);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
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
        private static BufferedInputStream bytesIn;
        private static final byte[] headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
        private static final byte[] requestLineDelimiter = new byte[]{'\r', '\n'};

        public ClientServer(Socket socket) {
            this.socket = socket;
            try {
                in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
                out = new BufferedOutputStream(this.socket.getOutputStream());
                bytesIn = new BufferedInputStream(this.socket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            Request req = createRequest();
            if (!getHandlers().containsKey(getKeyValue(req.getMethod(), req.getPath()))) {
                try {
                    badRequest(out);
                    return;
                } catch (IOException e) {
                }
            }
            chooseHandler(req);
        }

        private static void badRequest(BufferedOutputStream out) throws IOException {
            out.write((
                    "HTTP/1.1 400 Bad Request\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
        }

        private Request createRequest() {
            String[] parts;
            String path;
            String method;
            String[] requestLine;
            String body;
            Map<String, String> headers;
            Request request = null;
            try {
                var requestBytes = readRequest();
                requestLine = readRequestLine(requestBytes);
                method = requestLine[0];
                path = requestLine[1];
                headers = readHeaders(requestBytes);
                if (requestLine.length != 3) {
                    return null;
                }
                if (method.equals("GET")) {
                    bytesIn.skip(requestLine.length + requestLineDelimiter.length + headersDelimiter.length);
                    request = new Request.RequestBuilder()
                            .setMethod(method)
                            .setPath(path)
                            .setHeaders(headers)
                            .build();
                }
                if (!method.equals("GET")) {
                    body = readBody(requestBytes);
                    request = new Request.RequestBuilder()
                            .setMethod(method)
                            .setPath(path)
                            .setHeaders(headers)
                            .setBody(body)
                            .build();
                }
                return request;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        private void chooseHandler(Request request) {
            String key = getKeyValue(request.getMethod(), request.getPath());
            getHandlers().get(key).handle(request, out);
            System.out.println("request is sent");
        }

        // прочитать все сообщение
        private static byte[] readRequest() {
            final var limit = 4096;
            bytesIn.mark(limit);
            final var buffer = new byte[limit];
            try {
                bytesIn.read(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return buffer;
        }

        // прочитать requestLine
        private static String[] readRequestLine(byte[] buffer) throws IOException {
            final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, buffer.length);
            if (requestLineEnd == -1) {
                badRequest(out);
                return null;
            }
            // прочитать requestLine
            final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
            if (requestLine.length != 3) {
                badRequest(out);
                return null;
            }
            return requestLine;
        }

        // прочитать заголовки
        private static Map<String, String> readHeaders(byte[] buffer) throws IOException {
            Map<String, String> headerMap = new HashMap<>();
            // найти заголовки
            final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, buffer.length); // можно вынести в переменну/ класса и сохранить там при первом вызове
            final var headersStart = requestLineEnd + requestLineDelimiter.length;
            final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, buffer.length);
            if (headersEnd == -1) {
                badRequest(out);
                return null;
            }
            // отмотать на начало буфера
            bytesIn.reset();
            // пропустить requestLine
            bytesIn.skip(headersStart);
            final var headersBytes = bytesIn.readNBytes(headersEnd - headersStart);
            final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));
            for (String item : headers) {
                String[] line = item.split(" ");
                String key = line[0].replace(":", "").trim();
                headerMap.put(key, line[1]);
            }
            return headerMap;
        }

        private static Optional<String> getHeader(Map<String, String> headers, String header) {
            var item = headers.get(header);
            return Optional.of(item);
        }

        // прочитать тело
        private static String readBody(byte[] buffer) throws IOException {
            String body = null;
            var headers = readHeaders(buffer);
            final var contentLength = getHeader(headers, "Content-Length");
            if (contentLength.isPresent()) {
                bytesIn.skip(headersDelimiter.length);
                final var bodyLength = Integer.parseInt(contentLength.get());
                final var bodyBytes = bytesIn.readNBytes(bodyLength);
                body = new String(bodyBytes);
            }
            return body;
        }

        private static int indexOf(byte[] array, byte[] target, int start, int max) {
            outer:
            for (int i = start; i < max - target.length + 1; i++) {
                for (int j = 0; j < target.length; j++) {
                    if (array[i + j] != target[j]) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }
    }


}

