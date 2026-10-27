package library.desktop;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import library.application.CopyStatus;
import library.application.CopySummary;
import library.application.ForBrowsingTheLibrary;

/**
 * The loans as the Return and Renew dialogs list them. The library has no query for
 * loans, because a loan at the desk is a copy that is on loan, and the copies say
 * which they are; the two dialogs pick those out and describe each one the way
 * {@code Loan.toString()} did when it reached the screen in Chapter 7.
 */
class Loans {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM");

    static List<CopySummary> of(ForBrowsingTheLibrary library) {
        List<CopySummary> loans = new ArrayList<>();
        for (CopySummary copy : library.copies()) {
            if (copy.status() == CopyStatus.ON_LOAN) {
                loans.add(copy);
            }
        }
        return loans;
    }

    static String describe(CopySummary copy) {
        return copy.number() + " (" + copy.book() + "), " + copy.borrower() + ", due " + copy.dueDate().format(DAY);
    }
}
