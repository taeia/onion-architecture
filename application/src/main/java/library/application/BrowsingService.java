package library.application;

import java.util.ArrayList;
import java.util.List;

import library.domain.BookRepository;
import library.domain.CopyRepository;
import library.domain.MemberRepository;
import library.domain.QueueRepository;
import library.model.Book;
import library.model.Copy;
import library.model.Loan;
import library.model.Member;
import library.model.Queue;
import library.model.Reservation;

/**
 * The questions the drivers ask, all of them. An application service, and the class
 * behind the {@link ForBrowsingTheLibrary} port. It reads the aggregates through the
 * same repositories the commands use, because there is only one model, and copies what
 * a listing needs out of them into records. It saves nothing, it rings no signal, and
 * nothing it hands out can reach back into the model.
 */
public class BrowsingService implements ForBrowsingTheLibrary {

    private final BookRepository books;
    private final CopyRepository copies;
    private final MemberRepository members;
    private final QueueRepository queues;

    public BrowsingService(BookRepository books, CopyRepository copies, MemberRepository members,
                           QueueRepository queues) {
        this.books = books;
        this.copies = copies;
        this.members = members;
        this.queues = queues;
    }

    @Override
    public List<CopySummary> copies() {
        List<CopySummary> result = new ArrayList<>();
        for (Copy copy : copies.all()) {
            result.add(summarise(copy));
        }
        return result;
    }

    @Override
    public List<MemberSummary> members() {
        List<MemberSummary> result = new ArrayList<>();
        for (Member member : members.all()) {
            result.add(new MemberSummary(member.name(), member.loans().size(), member.debt()));
        }
        return result;
    }

    @Override
    public List<BookSummary> books() {
        List<BookSummary> result = new ArrayList<>();
        for (Book book : books.all()) {
            result.add(new BookSummary(book.isbn(), book.title()));
        }
        return result;
    }

    @Override
    public List<ReservationSummary> reservations() {
        List<ReservationSummary> result = new ArrayList<>();
        for (Queue queue : queues.all()) {
            for (Reservation reservation : queue.reservations()) {
                result.add(new ReservationSummary(queue.book().title(), reservation.member().name(),
                        queue.positionOf(reservation)));
            }
        }
        return result;
    }

    /**
     * The copy decides whether it is on hold or not; whether it is on loan is a fact
     * about some member, so the members are asked.
     */
    private CopySummary summarise(Copy copy) {
        String title = copy.book().title();
        if (copy.isOnHold()) {
            return new CopySummary(copy.number(), title, CopyStatus.ON_HOLD, null, null, copy.heldFor().name());
        }
        Loan loan = loanOf(copy);
        if (loan != null) {
            return new CopySummary(copy.number(), title, CopyStatus.ON_LOAN,
                    loan.member().name(), loan.dueDate(), null);
        }
        return new CopySummary(copy.number(), title, CopyStatus.AVAILABLE, null, null, null);
    }

    private Loan loanOf(Copy copy) {
        for (Member member : members.all()) {
            Loan loan = member.loanOf(copy);
            if (loan != null) {
                return loan;
            }
        }
        return null;
    }
}
