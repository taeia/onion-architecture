package library.application;

import java.util.List;

/**
 * Looking at the library without changing it. A driving port of queries only: every
 * method answers a question and leaves the library as it found it, so a driver may ask
 * as often as it likes, and that is what the window does whenever the
 * {@link ChangeSignal} rings. The answers are records shaped for a
 * screen or a listing, with dates and amounts as values, not as text, and with no
 * methods of their own; nothing a driver holds after asking can change the library.
 */
public interface ForBrowsingTheLibrary {

    List<CopySummary> copies();

    List<MemberSummary> members();

    List<BookSummary> books();

    List<ReservationSummary> reservations();
}
