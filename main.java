import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;

public class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", new HtmlHandler());
        server.createContext("/style.css", new CssHandler());
        server.createContext("/api", new ApiHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("Server running on port " + port);
    }

    // Serve HTML
    static class HtmlHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            byte[] response = Files.readAllBytes(new File("index.html").toPath());
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }
    }

    // Serve CSS
    static class CssHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            byte[] response = Files.readAllBytes(new File("style.css").toPath());
            exchange.getResponseHeaders().add("Content-Type", "text/css");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }
    }

    // API endpoint
    static class ApiHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String response = "{\"message\": \"Hello from Java Backend 🚀\"}";

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
        }
    }
}