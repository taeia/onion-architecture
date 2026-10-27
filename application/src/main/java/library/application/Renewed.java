package library.application;

import java.time.LocalDate;

/**
 * What a driver needs to say that a loan was renewed: whose loan it was, and the new
 * due date.
 */
public record Renewed(String member, LocalDate dueDate) {
}
