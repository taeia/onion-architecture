package library.application;

/**
 * The conversation about reservations: reserve a book. A driving port of one command.
 * The questions about books and queues are on {@link ForBrowsingTheLibrary}.
 */
public interface ForReservingBooks {

    /**
     * The member reserves the book. Returns the position of the new reservation in the
     * queue, counting from 1.
     */
    int reserve(String member, String book);
}
