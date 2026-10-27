package library.application;

import library.domain.CopyRepository;
import library.domain.Lending;
import library.domain.MemberRepository;
import library.model.Copy;
import library.model.LibraryException;
import library.model.Loan;
import library.model.Member;

/**
 * Borrowing, returning, and renewing. An application service, and the class behind the
 * {@link ForLendingCopies} port. Every command does the same four things: it turns the
 * names it was given into aggregates, asks {@link Lending} to do the work and turns a
 * refusal from the model into a {@link Refusal} at the port, rings the
 * {@link ChangeSignal}, and answers with the facts the caller's sentence needs. The
 * rules are in the aggregates, the saving is done by the domain service that changed
 * them, and the notification goes out through the {@link Notifier} port, as before.
 * The questions about loans and copies are gone from here; {@link BrowsingService}
 * answers them.
 */
public class LoanService implements ForLendingCopies {

    private final Lending lending;
    private final MemberRepository members;
    private final CopyRepository copies;
    private final Notifier notifier;
    private final ChangeSignal signal;

    public LoanService(Lending lending, MemberRepository members, CopyRepository copies,
                       Notifier notifier, ChangeSignal signal) {
        this.lending = lending;
        this.members = members;
        this.copies = copies;
        this.notifier = notifier;
        this.signal = signal;
    }

    @Override
    public Borrowed borrow(String memberName, String copyNumber) {
        Member member = member(memberName);
        Copy copy = copy(copyNumber);
        Loan loan;
        try {
            loan = lending.borrow(member, copy);
        } catch (LibraryException e) {
            throw new Refusal(e.getMessage());
        }
        signal.changed();
        return new Borrowed(copy.book().title(), loan.dueDate());
    }

    @Override
    public Returned returnCopy(String copyNumber) {
        Copy copy = copy(copyNumber);
        Loan loan = loanOf(copy);
        Member member = loan.member();
        long daysLate = lending.returnCopy(loan);
        if (copy.isOnHold()) {
            notifier.send(copy.heldFor(), copy.book().title() + " is waiting for you.");
        }
        signal.changed();
        return new Returned(member.name(), daysLate, member.debt(),
                copy.isOnHold() ? copy.heldFor().name() : null);
    }

    @Override
    public Renewed renew(String copyNumber) {
        Loan loan = loanOf(copy(copyNumber));
        try {
            lending.renew(loan);
        } catch (LibraryException e) {
            throw new Refusal(e.getMessage());
        }
        signal.changed();
        return new Renewed(loan.member().name(), loan.dueDate());
    }

    // ---- from the names the drivers use to the aggregates ----

    private Member member(String name) {
        Member member = members.named(name);
        if (member == null) {
            throw new Refusal("There is no member called " + name + ".");
        }
        return member;
    }

    private Copy copy(String number) {
        Copy copy = copies.numbered(number);
        if (copy == null) {
            throw new Refusal("There is no copy " + number + ".");
        }
        return copy;
    }

    /**
     * Loans live inside members, so the question is put to each member in turn.
     */
    private Loan loanOf(Copy copy) {
        for (Member member : members.all()) {
            Loan loan = member.loanOf(copy);
            if (loan != null) {
                return loan;
            }
        }
        throw new Refusal(copy.number() + " is not on loan.");
    }
}
