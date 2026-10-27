package library.application;

import library.domain.MemberRepository;
import library.model.LibraryException;
import library.model.Member;
import library.model.Money;

/**
 * Paying fines. An application service, and the class behind the {@link ForPayingFines}
 * port: it finds the member by name, and the member knows how to pay; paying touches
 * one aggregate, so no domain service is needed, and this class saves the result and
 * rings the {@link ChangeSignal}. The list of members, which every driver asked this
 * class for in Chapter 7, is now given out by {@link BrowsingService}.
 */
public class MemberService implements ForPayingFines {

    private final MemberRepository members;
    private final ChangeSignal signal;

    public MemberService(MemberRepository members, ChangeSignal signal) {
        this.members = members;
        this.signal = signal;
    }

    @Override
    public Money pay(String memberName) {
        Member member = member(memberName);
        Money paid;
        try {
            paid = member.pay();
        } catch (LibraryException e) {
            throw new Refusal(e.getMessage());
        }
        members.save(member);
        signal.changed();
        return paid;
    }

    private Member member(String name) {
        Member member = members.named(name);
        if (member == null) {
            throw new Refusal("There is no member called " + name + ".");
        }
        return member;
    }
}
