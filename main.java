import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;

public class Main {
    public static void main(String[] args) throws Exception {

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // API endpoint
        server.createContext("/api", new ApiHandler());

        // Serve HTML
        server.createContext("/", new FileHandler("index.html", "text/html"));
        server.createContext("/style.css", new FileHandler("style.css", "text/css"));

        server.setExecutor(null);
        server.start();

        System.out.println("Server running on port " + port);
    }

    static class ApiHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"message\": \"Hello from Java Backend 🚀\"}";

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());

            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    static class FileHandler implements HttpHandler {
        private String fileName;
        private String contentType;

        public FileHandler(String fileName, String contentType) {
            this.fileName = fileName;
            this.contentType = contentType;
        }

        public void handle(HttpExchange exchange) throws IOException {
            File file = new File(fileName);

            if (!file.exists()) {
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
                return;
            }

            byte[] bytes = new FileInputStream(file).readAllBytes();

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
