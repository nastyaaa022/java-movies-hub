package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;
import java.util.*;

public class MoviesStore {
    private Map<Long, Movie> movies = new HashMap<>();
    private Long currentId = 1L;

    public void addMovie(Movie movie) {
        if (movie.getId() == null) {
            movie.setId(currentId++);
        }
        if (movies.containsKey(movie.getId())) {
            throw new IllegalArgumentException("Фильм с таким ID уже существует: " + movie.getId());
        }
        movies.put(movie.getId(), movie);
    }

    public Movie findById(Long id) {
        return movies.get(id);
    }

    public boolean deleteMovie(Long id) {
        if (movies.containsKey(id)) {
            movies.remove(id);
            return true;
        }
        return false;
    }

    public List<Movie> getAllMovies() {
        return new ArrayList<>(movies.values());
    }

    public void clear() {
        movies.clear();
        currentId = 1L;
    }
}
