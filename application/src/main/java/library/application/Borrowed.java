package library.application;

import java.time.LocalDate;

/**
 * What a driver needs to say that a copy was borrowed: the title of the book, which
 * the caller named only by the copy's number, and the due date.
 */
public record Borrowed(String book, LocalDate dueDate) {
}
