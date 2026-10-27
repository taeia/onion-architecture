package library.application;

/**
 * One reservation as a listing shows it: the book, the member, and the position in
 * the queue, counting from 1.
 */
public record ReservationSummary(String book, String member, int position) {
}
