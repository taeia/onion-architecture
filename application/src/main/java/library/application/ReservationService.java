package library.application;

import library.domain.BookRepository;
import library.domain.MemberRepository;
import library.domain.QueueRepository;
import library.domain.Reserving;
import library.model.Book;
import library.model.LibraryException;
import library.model.Member;
import library.model.Reservation;

/**
 * Reserving. An application service, and the class behind the {@link ForReservingBooks}
 * port: it finds the member and the book by name, asks {@link Reserving} to do the
 * work, rings the {@link ChangeSignal}, and reads the position back. The questions
 * about books and reservations are answered by {@link BrowsingService}.
 */
public class ReservationService implements ForReservingBooks {

    private final Reserving reserving;
    private final BookRepository books;
    private final MemberRepository members;
    private final QueueRepository queues;
    private final ChangeSignal signal;

    public ReservationService(Reserving reserving, BookRepository books, MemberRepository members,
                              QueueRepository queues, ChangeSignal signal) {
        this.reserving = reserving;
        this.books = books;
        this.members = members;
        this.queues = queues;
        this.signal = signal;
    }

    @Override
    public int reserve(String memberName, String title) {
        Member member = member(memberName);
        Book book = book(title);
        Reservation reservation;
        try {
            reservation = reserving.reserve(member, book);
        } catch (LibraryException e) {
            throw new Refusal(e.getMessage());
        }
        signal.changed();
        return queues.of(book).positionOf(reservation);
    }

    private Member member(String name) {
        Member member = members.named(name);
        if (member == null) {
            throw new Refusal("There is no member called " + name + ".");
        }
        return member;
    }

    private Book book(String title) {
        Book book = books.titled(title);
        if (book == null) {
            throw new Refusal("There is no book called " + title + ".");
        }
        return book;
    }
}
