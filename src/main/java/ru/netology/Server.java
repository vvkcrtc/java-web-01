package ru.netology;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Server {
    protected static List<String> validPaths = new ArrayList<>();
    protected static int serverPort;
    ServerSocket server = null;

    public Server(int serverPort, List<String> validPaths) {
        this.serverPort = serverPort;
        this.validPaths.addAll(validPaths);
    }


    public void run() throws IOException {

        try {
            server = new ServerSocket(serverPort);
            server.setReuseAddress(true);

            ExecutorService executorService = Executors.newFixedThreadPool(64);

            while (true) {

                Socket client = server.accept();
/*
                System.out.println("New client connected"
                        + client.getInetAddress()
                        .getHostAddress());
*/
                executorService.execute(new ClientHandler(client));

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (server != null) {
                try {
                    server.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // ClientHandler class
    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        // Constructor
        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        protected static boolean checkCorrectPath(BufferedOutputStream out, String path) throws IOException {
            if (!validPaths.contains(path)) {
                out.write((
                        "HTTP/1.1 404 Not Found\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.flush();
                return false;
            }
            return true;
        }

        protected static void sendClassic(BufferedOutputStream out, Path filePath, String mimeType) throws IOException {
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
        }

        protected static void sendFile(BufferedOutputStream out, Path filePath, String mimeType) throws IOException {
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

        }

        public void run() {
            BufferedOutputStream out = null;
            BufferedReader in = null;
            try {

                out = new BufferedOutputStream(clientSocket.getOutputStream());
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                while (true) {
                    final var requestLine = in.readLine();
                    System.out.println("Request line :" + requestLine);

                    if (requestLine == null) {
                        break;
                        //continue;
                    }
                    final var parts = requestLine.split(" ");
                    System.out.println("Parts :");
                    for (int i = 0; i < parts.length; i++) {
                        System.out.print(" " + parts[i]);
                    }
                    System.out.println(" ");

                    if (parts.length != 3) {
                        // just close socket
                        continue;
                    }

                    final var path = parts[1];
                    if (!checkCorrectPath(out, path)) {
                        continue;
                    }

                    final var filePath = Path.of(".", "public", path);
                    final var mimeType = Files.probeContentType(filePath);

                    // special case for classic
                    if (path.equals("/classic.html")) {
                        sendClassic(out, filePath, mimeType);
                        continue;
                    }

                    sendFile(out, filePath, mimeType);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                    if (in != null) {
                        in.close();
                        clientSocket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
