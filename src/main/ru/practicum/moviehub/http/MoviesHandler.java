package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore moviesStore;
    private final Gson gson = new Gson();

    private static final int MAX_TITLE_LENGTH = 100;
    private static final int FIRST_MOVIE_YEAR = 1888;

    public MoviesHandler(MoviesStore store) {
        this.moviesStore = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/movies")) {
                handleMoviesCollection(exchange, method);
            } else if (path.startsWith("/movies/")) {
                handleMovieById(exchange, method, path);
            } else {
                handleNotFound(exchange);
            }
        } catch (Exception e) {
            handleInternalError(exchange, e);
        } finally {
            exchange.close();
        }
    }

    private void handleMoviesCollection(HttpExchange exchange, String method) throws IOException {
        if ("GET".equalsIgnoreCase(method)) {
            handleGetMovies(exchange);
        } else if ("POST".equalsIgnoreCase(method)) {
            handlePostMovie(exchange);
        } else {
            handleMethodNotAllowed(exchange, "GET, POST");
        }
    }

    private void handleMovieById(HttpExchange exchange, String method, String path) throws IOException {
        String idStr = path.substring("/movies/".length());

        Long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            ErrorResponse error = new ErrorResponse("Некорректный ID. ID должен быть числом");
            sendJson(exchange, 400, convertErrorToJson(error));
            return;
        }

        if ("GET".equalsIgnoreCase(method)) {
            handleGetMovieById(exchange, id);
        } else if ("DELETE".equalsIgnoreCase(method)) {
            handleDeleteMovie(exchange, id);
        } else {
            handleMethodNotAllowed(exchange, "GET, DELETE");
        }
    }

    private void handleGetMovies(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        String query = uri.getQuery();

        if (query != null && query.contains("year=")) {
            try {
                handleGetMoviesByYear(exchange, query);
            } catch (IllegalArgumentException e) {
                ErrorResponse error = new ErrorResponse(e.getMessage());
                sendJson(exchange, 400, convertErrorToJson(error));
            }
        } else {
            List<Movie> movies = moviesStore.getAllMovies();
            String json = convertMoviesToJson(movies);
            sendJson(exchange, 200, json);
        }
    }

    private void handleGetMoviesByYear(HttpExchange exchange, String query) throws IOException {
        Integer year = parseYearFromQuery(query);

        if (year == null) {
            List<Movie> movies = moviesStore.getAllMovies();
            String json = convertMoviesToJson(movies);
            sendJson(exchange, 200, json);
            return;
        }

        int currentYear = LocalDate.now().getYear();
        int maxYear = currentYear + 1;

        if (year < FIRST_MOVIE_YEAR || year > maxYear) {
            ErrorResponse error = new ErrorResponse(
                    String.format("Год должен быть в диапазоне от %d до %d", FIRST_MOVIE_YEAR, maxYear)
            );
            sendJson(exchange, 400, convertErrorToJson(error));
            return;
        }

        final Integer filterYear = year;
        List<Movie> allMovies = moviesStore.getAllMovies();
        List<Movie> filteredMovies = allMovies.stream()
                .filter(movie -> movie.getYear() != null && movie.getYear().equals(filterYear))
                .collect(Collectors.toList());

        String json = convertMoviesToJson(filteredMovies);
        sendJson(exchange, 200, json);
    }

    private Integer parseYearFromQuery(String query) {
        try {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("year=")) {
                    String yearStr = param.substring(5);

                    if (yearStr.isEmpty()) {
                        throw new IllegalArgumentException("Параметр 'year' не может быть пустым");
                    }

                    return Integer.parseInt(yearStr);
                }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректный параметр запроса — 'year'. Ожидается число");
        }
        return null;
    }

    private void handleGetMovieById(HttpExchange exchange, Long id) throws IOException {
        Movie movie = moviesStore.findById(id);

        if (movie == null) {
            ErrorResponse error = new ErrorResponse("Фильм не найден");
            sendJson(exchange, 404, convertErrorToJson(error));
            return;
        }

        String json = convertMovieToJson(movie);
        sendJson(exchange, 200, json);
    }

    private void handleDeleteMovie(HttpExchange exchange, Long id) throws IOException {
        boolean deleted = moviesStore.deleteMovie(id);

        if (deleted) {
            exchange.sendResponseHeaders(204, -1);
        } else {
            ErrorResponse error = new ErrorResponse("Фильм не найден");
            sendJson(exchange, 404, convertErrorToJson(error));
        }
    }

    private void handlePostMovie(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            ErrorResponse error = new ErrorResponse("Unsupported Media Type. Expected: application/json");
            sendJson(exchange, 415, convertErrorToJson(error));
            return;
        }

        String requestBody = readRequestBody(exchange);
        if (requestBody == null || requestBody.trim().isEmpty()) {
            ErrorResponse error = new ErrorResponse("Request body is empty");
            sendJson(exchange, 422, convertErrorToJson(error));
            return;
        }

        Movie movie;
        try {
            movie = parseMovieFromJson(requestBody);
        } catch (IllegalArgumentException e) {
            ErrorResponse error = new ErrorResponse("Invalid JSON format: " + e.getMessage());
            sendJson(exchange, 422, convertErrorToJson(error));
            return;
        }

        List<String> validationErrors = validateMovie(movie);
        if (!validationErrors.isEmpty()) {
            ErrorResponse error = ErrorResponse.validationError(validationErrors);
            sendJson(exchange, 422, convertErrorToJson(error));
            return;
        }

        try {
            moviesStore.addMovie(movie);
        } catch (IllegalArgumentException e) {
            ErrorResponse error = new ErrorResponse("Failed to add movie: " + e.getMessage());
            sendJson(exchange, 422, convertErrorToJson(error));
            return;
        }

        String json = convertMovieToJson(movie);
        sendJson(exchange, 201, json);
    }

    private void handleNotFound(HttpExchange exchange) throws IOException {
        ErrorResponse error = new ErrorResponse("Ресурс не найден");
        sendJson(exchange, 404, convertErrorToJson(error));
    }

    private void handleMethodNotAllowed(HttpExchange exchange, String allowedMethods) throws IOException {
        ErrorResponse error = new ErrorResponse("Method not allowed");
        String json = convertErrorToJson(error);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Allow", allowedMethods);
        exchange.sendResponseHeaders(405, json.getBytes(StandardCharsets.UTF_8).length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void handleInternalError(HttpExchange exchange, Exception e) throws IOException {
        ErrorResponse error = new ErrorResponse("Internal server error: " + e.getMessage());
        String json = convertErrorToJson(error);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(500, json.getBytes(StandardCharsets.UTF_8).length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
            return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
        }
    }

    private String convertMovieToJson(Movie movie) {
        StringBuilder json = new StringBuilder();
        json.append("{")
                .append("\"id\": ").append(movie.getId())
                .append(", \"title\": \"").append(escapeJson(movie.getTitle())).append("\"")
                .append(", \"year\": ").append(movie.getYear());

        if (movie.getDirector() != null && !movie.getDirector().isEmpty()) {
            json.append(", \"director\": \"").append(escapeJson(movie.getDirector())).append("\"");
        }

        json.append("}");
        return json.toString();
    }

    private String convertMoviesToJson(List<Movie> movies) {
        if (movies.isEmpty()) {
            return "[]";
        }

        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("[");

        for (int i = 0; i < movies.size(); i++) {
            jsonBuilder.append(convertMovieToJson(movies.get(i)));
            if (i < movies.size() - 1) {
                jsonBuilder.append(",");
            }
        }

        jsonBuilder.append("]");
        return jsonBuilder.toString();
    }

    private String convertErrorToJson(ErrorResponse error) {
        StringBuilder json = new StringBuilder();
        json.append("{")
                .append("\"error\": \"").append(escapeJson(error.getError())).append("\"");

        if (!error.getDetails().isEmpty()) {
            json.append(", \"details\": [");
            for (int i = 0; i < error.getDetails().size(); i++) {
                json.append("\"").append(escapeJson(error.getDetails().get(i))).append("\"");
                if (i < error.getDetails().size() - 1) {
                    json.append(",");
                }
            }
            json.append("]");
        }

        json.append("}");
        return json.toString();
    }

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private Movie parseMovieFromJson(String json) throws IllegalArgumentException {
        String trimmedJson = json.trim();
        if (!trimmedJson.contains("\"title\"") || !trimmedJson.contains("\"year\"")) {
            throw new IllegalArgumentException("JSON keys must be quoted");
        }

        try {
            JsonObject jsonObject = gson.fromJson(json, JsonObject.class);

            if (jsonObject == null) {
                throw new IllegalArgumentException("Invalid JSON");
            }

            if (!jsonObject.has("title") || !jsonObject.has("year")) {
                throw new IllegalArgumentException("Missing required fields: title and year");
            }

            String title = jsonObject.get("title").getAsString();
            int year = jsonObject.get("year").getAsInt();
            String director = null;

            if (jsonObject.has("director")) {
                director = jsonObject.get("director").getAsString();
            }

            return new Movie(null, title, year, director);

        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage());
        } catch (IllegalStateException | NumberFormatException e) {
            throw new IllegalArgumentException("Invalid field type: " + e.getMessage());
        }
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();

        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            errors.add("название не должно быть пустым");
        } else if (movie.getTitle().trim().length() > MAX_TITLE_LENGTH) {
            errors.add("длина названия не должна превышать " + MAX_TITLE_LENGTH + " символов");
        }

        if (movie.getYear() == null) {
            errors.add("год обязателен");
        } else {
            int year = movie.getYear();
            int currentYear = LocalDate.now().getYear();
            int maxYear = currentYear + 1;

            if (year < FIRST_MOVIE_YEAR || year > maxYear) {
                errors.add("год должен быть между " + FIRST_MOVIE_YEAR + " и " + maxYear);
            }
        }

        return errors;
    }
}