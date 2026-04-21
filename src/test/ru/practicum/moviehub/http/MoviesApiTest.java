package ru.practicum.moviehub.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;
    private static MoviesStore moviesStore;

    @BeforeAll
    static void beforeAll() throws InterruptedException {
        moviesStore = new MoviesStore();
        server = new MoviesServer(moviesStore, 8080);
        server.start();

        Thread.sleep(1000);

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        moviesStore.clear();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body().trim());

        String contentTypeHeaderValue = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue);
    }

    @Test
    void getMovies_whenHasMovies_returnsMoviesArray() throws Exception {
        Movie movie1 = new Movie(null, "Фильм 1", 2020, "Режиссёр 1");
        Movie movie2 = new Movie(null, "Фильм 2", 2021, "Режиссёр 2");
        moviesStore.addMovie(movie1);
        moviesStore.addMovie(movie2);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"));
        assertTrue(body.contains("Фильм 1"));
        assertTrue(body.contains("Фильм 2"));
    }

    @Test
    void postMovie_withValidData_returns201AndCreatedMovie() throws Exception {
        String jsonBody = "{\"title\": \"Интерстеллар\", \"year\": 2014}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(201, resp.statusCode());

        String body = resp.body().trim();
        assertTrue(body.contains("\"id\": 1"));
        assertTrue(body.contains("\"title\": \"Интерстеллар\""));
        assertTrue(body.contains("\"year\": 2014"));
    }

    @Test
    void postMovie_withoutContentType_returns415() throws Exception {
        String jsonBody = "{\"title\": \"Фильм\", \"year\": 2020}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode());

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\": \"Unsupported Media Type. Expected: application/json\""));
    }

    @Test
    void postMovie_withEmptyTitle_returns422() throws Exception {
        String jsonBody = "{\"title\": \"\", \"year\": 2020}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\": \"Ошибка валидации\""));
        assertTrue(body.contains("\"details\":"));
        assertTrue(body.contains("название не должно быть пустым"));
    }

    @Test
    void postMovie_withInvalidYear_returns422() throws Exception {
        String jsonBody = "{\"title\": \"Фильм\", \"year\": 1800}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\": \"Ошибка валидации\""));
        assertTrue(body.contains("\"details\":"));
        assertTrue(body.contains("год должен быть между"));
    }

    @Test
    void postMovie_withTooLongTitle_returns422() throws Exception {
        String longTitle = "A".repeat(101);
        String jsonBody = "{\"title\": \"" + longTitle + "\", \"year\": 2020}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\": \"Ошибка валидации\""));
        assertTrue(body.contains("\"details\":"));
        assertTrue(body.contains("длина названия не должна превышать"));
    }

    @Test
    void postMovie_withInvalidJson_returns422() throws Exception {
        String invalidJson = "{title: \"Фильм\", year: 2020}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\": \"Invalid JSON format:"));
    }

    @Test
    void getMovieById_whenMovieExists_returns200AndMovie() throws Exception {
        Movie movie = new Movie(null, "Интерстеллар", 2014, "Кристофер Нолан");
        moviesStore.addMovie(movie);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        String body = resp.body().trim();
        assertTrue(body.contains("\"id\": 1"));
        assertTrue(body.contains("\"title\": \"Интерстеллар\""));
        assertTrue(body.contains("\"year\": 2014"));
        assertTrue(body.contains("\"director\": \"Кристофер Нолан\""));
    }

    @Test
    void getMovieById_whenMovieNotFound_returns404() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode());

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\": \"Фильм не найден, ID фильма: 999\""));
    }

    @Test
    void getMovieById_whenIdIsNotNumber_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\": \"Некорректный ID. ID должен быть числом\""));
    }

    @Test
    void deleteMovie_whenMovieExists_returns204() throws Exception {
        Movie movie = new Movie(null, "Фильм для удаления", 2020, "Режиссёр");
        moviesStore.addMovie(movie);

        assertEquals(1, moviesStore.getAllMovies().size());

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .DELETE()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(204, resp.statusCode());
        assertEquals(0, resp.body().length());
        assertEquals(0, moviesStore.getAllMovies().size());
    }

    @Test
    void deleteMovie_whenMovieNotFound_returns404() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .DELETE()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode());

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\": \"Фильм не найден, ID фильма: 999\""));
    }

    @Test
    void deleteMovie_whenIdIsNotNumber_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .DELETE()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\": \"Некорректный ID. ID должен быть числом\""));
    }

    @Test
    void getMoviesByYear_whenValidYear_returnsFilteredMovies() throws Exception {
        moviesStore.addMovie(new Movie(null, "Фильм 2020", 2020, "Режиссёр 1"));
        moviesStore.addMovie(new Movie(null, "Другой фильм 2020", 2020, "Режиссёр 2"));
        moviesStore.addMovie(new Movie(null, "Фильм 2021", 2021, "Режиссёр 3"));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2020"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"));
        assertTrue(body.contains("Фильм 2020"));
        assertTrue(body.contains("Другой фильм 2020"));
        assertFalse(body.contains("Фильм 2021"));
    }

    @Test
    void getMoviesByYear_whenNoMoviesForYear_returnsEmptyArray() throws Exception {
        moviesStore.addMovie(new Movie(null, "Фильм 2019", 2019, "Режиссёр"));
        moviesStore.addMovie(new Movie(null, "Фильм 2021", 2021, "Режиссёр"));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2020"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body().trim());
    }

    @Test
    void getMoviesByYear_whenYearTooSmall_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=1800"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\":"));
        assertTrue(body.contains("Год должен быть в диапазоне"));
    }

    @Test
    void getMoviesByYear_whenYearTooLarge_returns400() throws Exception {
        int futureYear = LocalDate.now().getYear() + 2;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=" + futureYear))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\":"));
        assertTrue(body.contains("Год должен быть в диапазоне"));
    }

    @Test
    void getMoviesByYear_whenYearNotNumber_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=abc"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\": \"Некорректный параметр запроса — 'year'. Ожидается число\""));
    }

    @Test
    void getMoviesByYear_whenYearParamEmpty_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year="))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\":"));
        assertTrue(body.contains("Параметр 'year' не может быть пустым") ||
                body.contains("Некорректный параметр запроса"));
    }
}