package ru.practicum.moviehub.model;

import java.time.LocalDate;
import java.util.Objects;

public class Movie {
    private Long id;
    private String title;
    private Integer year;
    private String director;

    public Movie() {
    }

    public Movie(Long id, String title, Integer year, String director) {
        this.id = id;
        this.title = title;
        this.year = year;
        this.director = director;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Название фильма не может быть пустым");
        }
        this.title = title;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        if (year == null || year < 1888 || year > LocalDate.now().getYear()) {
            throw new IllegalArgumentException("Год выпуска указан некорректно");
        }
        this.year = year;
    }

    public String getDirector() {
        return director;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    @Override
    public String toString() {
        return "Movie{" +
                "id=" + id +
                ", title='" + title + "'" +
                ", year=" + year +
                ", director='" + director + "'" +
                "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Movie movie = (Movie) o;
        return Objects.equals(id, movie.id) &&
                Objects.equals(title, movie.title) &&
                Objects.equals(year, movie.year) &&
                Objects.equals(director, movie.director);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, year, director);
    }
}