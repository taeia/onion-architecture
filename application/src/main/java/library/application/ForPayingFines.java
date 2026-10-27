package library.application;

import library.model.Money;

/**
 * The conversation about money: pay what is owed. A driving port of one command. The
 * list of members, which every driver asked for here in Chapter 7, is on
 * {@link ForBrowsingTheLibrary}, where it belongs.
 */
public interface ForPayingFines {

    /**
     * The member pays the whole debt. Returns the amount paid.
     */
    Money pay(String member);
}
