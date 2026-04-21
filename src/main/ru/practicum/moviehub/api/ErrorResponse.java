package ru.practicum.moviehub.api;

import java.util.ArrayList;
import java.util.List;

public class ErrorResponse {
     private String error;
     private List<String> details;

     public ErrorResponse(String error) {
          this.error = error;
          this.details = new ArrayList<>();
     }

     public ErrorResponse(String error, List<String> details) {
          this.error = error;
          this.details = details != null ? details : new ArrayList<>();
     }

     public static ErrorResponse validationError(List<String> details) {
          return new ErrorResponse("Ошибка валидации", details);
     }

     public String getError() {
          return error;
     }

     public List<String> getDetails() {
          return details;
     }

     public void setError(String error) {
          this.error = error;
     }

     public void setDetails(List<String> details) {
          this.details = details;
     }
}