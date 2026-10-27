package library.application;

/**
 * The conversation the outside world has with the library about lending: borrow a copy,
 * return it, renew the loan. A driving port, and now a port of commands only: each
 * method changes the library and is told what to change by name, a member's name and a
 * copy's number, which is what a line of text, a dialog, or a URL can carry. Each one
 * answers with the few facts the caller needs for its sentence and nothing more, and
 * never with a {@code Member}, a {@code Copy}, or a {@code Loan}. The questions that
 * used to sit beside these methods are on {@link ForBrowsingTheLibrary}. A command the
 * library refuses ends in a {@link Refusal}.
 */
public interface ForLendingCopies {

    /**
     * The member borrows the copy. Answers with the book and the due date.
     */
    Borrowed borrow(String member, String copy);

    /**
     * The copy comes back. Answers with who returned it, how late, what they owe now,
     * and whom it is held for, if anyone.
     */
    Returned returnCopy(String copy);

    /**
     * The loan of the copy is renewed. Answers with whose loan it was and the new due
     * date.
     */
    Renewed renew(String copy);
}
