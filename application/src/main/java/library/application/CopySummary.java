package library.application;

import java.time.LocalDate;

/**
 * One copy as a listing shows it. The status is decided by the core, so that no
 * driver has to know what "available" means; the borrower and the due date are set
 * only when the copy is on loan, and the member it is held for only when it is on
 * hold. A record with no behaviour: holding one changes nothing.
 */
public record CopySummary(String number, String book, CopyStatus status,
                          String borrower, LocalDate dueDate, String heldFor) {
}
