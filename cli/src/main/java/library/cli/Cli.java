package library.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

import library.application.BrowsingService;
import library.application.LoanService;
import library.application.MemberService;
import library.application.ReservationService;
import library.domain.Lending;
import library.domain.Reserving;
import library.inmemory.InMemoryBookRepository;
import library.inmemory.InMemoryCopyRepository;
import library.inmemory.InMemoryMemberRepository;
import library.inmemory.InMemoryQueueRepository;
import library.inmemory.SettableClock;
import library.inmemory.StartingState;
import library.application.Borrowed;
import library.application.CopySummary;
import library.application.ForBrowsingTheLibrary;
import library.application.ForLendingCopies;
import library.application.ForPayingFines;
import library.application.ForReservingBooks;
import library.application.MemberSummary;
import library.application.Refusal;
import library.application.Renewed;
import library.application.ReservationSummary;
import library.application.Returned;

/**
 * The library on the command line. A driving adapter: it reads commands written in the
 * words of Chapter 0, calls the four driving ports, and prints what happened. The names
 * on the line, John, C1, The Hobbit, are the names the ports take, so a command is one
 * call with the words passed straight through, and the listings are the records the
 * browsing port returns, printed one per line. It names a class from the application
 * package only in {@link #main}, which is the configurator for this program.
 *
 * Commands are one per line. {@code day 2 March} moves the clock. {@code borrow John C1},
 * {@code return C1}, {@code renew C1}, {@code reserve Mary The Hobbit}, and
 * {@code pay Jane} are the five operations. {@code copies}, {@code members}, and
 * {@code reservations} show the library, {@code reset} puts it back in its starting
 * state, and {@code quit} ends the session. Dates are in 2026, the year of the scenarios.
 */
public class Cli {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_WITH_YEAR = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d MMMM uuuu")
            .toFormatter(Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.STRICT);

    private final ForLendingCopies lending;
    private final ForReservingBooks reserving;
    private final ForPayingFines paying;
    private final ForBrowsingTheLibrary library;
    private final SettableClock clock;
    private final Runnable reset;
    private final PrintStream out;

    public Cli(ForLendingCopies lending, ForReservingBooks reserving, ForPayingFines paying,
               ForBrowsingTheLibrary library, SettableClock clock, Runnable reset, PrintStream out) {
        this.lending = lending;
        this.reserving = reserving;
        this.paying = paying;
        this.library = library;
        this.clock = clock;
        this.reset = reset;
        this.out = out;
    }

    /**
     * Reads commands from a script file if one is given, otherwise from the terminal.
     */
    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.ENGLISH);

        // The configurator for the command line: the same core as the window, the same
        // in-memory storage and clock, a different mailbox, and nobody on the change
        // signal. The window keeps the messages in a list it shows; the command line
        // prints them as they are sent. The window refreshes when the signal rings;
        // the command line prints what it was told and has no tables to refresh.
        InMemoryBookRepository books = new InMemoryBookRepository();
        InMemoryCopyRepository copies = new InMemoryCopyRepository();
        InMemoryMemberRepository members = new InMemoryMemberRepository();
        InMemoryQueueRepository queues = new InMemoryQueueRepository();
        SettableClock clock = new SettableClock();
        ConsoleNotifier notifier = new ConsoleNotifier(System.out);
        Lending lending = new Lending(members, copies, queues, clock);
        Reserving reserving = new Reserving(copies, queues);
        Runnable reset = () -> StartingState.load(books, copies, members, queues);
        reset.run();
        Cli cli = new Cli(new LoanService(lending, members, copies, notifier, () -> {}),
                new ReservationService(reserving, books, members, queues, () -> {}),
                new MemberService(members, () -> {}),
                new BrowsingService(books, copies, members, queues),
                clock, reset, System.out);

        if (args.length > 0) {
            try (BufferedReader script = Files.newBufferedReader(Path.of(args[0]))) {
                cli.run(script, true);
            }
        } else {
            cli.run(new BufferedReader(new InputStreamReader(System.in)), false);
        }
    }

    /**
     * Runs commands until the input ends or a {@code quit}. When reading a script, each
     * command is echoed behind a prompt, so that the output reads like a session.
     */
    public void run(BufferedReader in, boolean echo) throws IOException {
        if (!echo) {
            out.print("> ");
        }
        String line;
        while ((line = in.readLine()) != null) {
            line = line.strip();
            if (!line.isEmpty() && !line.startsWith("#")) {
                if (echo) {
                    out.println("> " + line);
                }
                out.println(execute(line));
                if (line.equals("quit")) {
                    return;
                }
            }
            if (!echo) {
                out.print("> ");
            }
        }
    }

    /**
     * Executes one command and returns the sentence to print.
     */
    public String execute(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0];
        String rest = words.length > 1 ? words[1].strip().replaceAll("\\s+", " ") : "";
        try {
            return switch (command) {
                case "day" -> day(rest);
                case "borrow" -> borrow(rest);
                case "return" -> returnCopy(rest);
                case "renew" -> renew(rest);
                case "reserve" -> reserve(rest);
                case "pay" -> pay(rest);
                case "copies" -> copies();
                case "members" -> members();
                case "reservations" -> reservations();
                case "reset" -> reset();
                case "help" -> help();
                case "quit" -> "Goodbye.";
                default -> "Unknown command: " + command + ". Type help.";
            };
        } catch (Refusal e) {
            return "The library refuses. " + e.getMessage();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    // ---- the clock ----

    private String day(String text) {
        try {
            clock.set(LocalDate.parse(text + " 2026", DAY_WITH_YEAR));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Write the day as in \"day 2 March\".");
        }
        return "It is " + clock.today().format(DAY) + ".";
    }

    // ---- the five operations ----

    private String borrow(String rest) {
        String[] words = twoWords(rest, "borrow John C1");
        Borrowed borrowed = lending.borrow(words[0], words[1]);
        return words[0] + " borrowed " + words[1] + " of " + borrowed.book()
                + ". It is due on " + borrowed.dueDate().format(DAY) + ".";
    }

    private String returnCopy(String rest) {
        String copy = oneWord(rest, "return C1");
        Returned returned = lending.returnCopy(copy);
        String result;
        if (returned.daysLate() > 0) {
            result = returned.member() + " returned " + copy + " " + returned.daysLate()
                    + " days late and now owes " + returned.debt() + ".";
        } else {
            result = returned.member() + " returned " + copy + " on time.";
        }
        if (returned.heldFor() != null) {
            result += " " + copy + " is on hold for " + returned.heldFor() + ", who has been notified.";
        }
        return result;
    }

    private String renew(String rest) {
        String copy = oneWord(rest, "renew C1");
        Renewed renewed = lending.renew(copy);
        return renewed.member() + " renewed the loan of " + copy
                + ". It is now due on " + renewed.dueDate().format(DAY) + ".";
    }

    private String reserve(String rest) {
        String[] words = twoWords(rest, "reserve Mary The Hobbit");
        int position = reserving.reserve(words[0], words[1]);
        return words[0] + " reserved " + words[1] + ". Position in the queue: " + position + ".";
    }

    private String pay(String rest) {
        String member = oneWord(rest, "pay Jane");
        return member + " paid " + paying.pay(member) + " and owes nothing now.";
    }

    // ---- the library as it stands ----

    private String copies() {
        StringBuilder result = new StringBuilder();
        for (CopySummary copy : library.copies()) {
            result.append(copy.number()).append("  ").append(copy.book()).append(": ");
            switch (copy.status()) {
                case ON_HOLD -> result.append("on hold for ").append(copy.heldFor());
                case ON_LOAN -> result.append("on loan to ").append(copy.borrower())
                        .append(", due ").append(copy.dueDate().format(DAY));
                case AVAILABLE -> result.append("available");
            }
            result.append('\n');
        }
        return result.toString().stripTrailing();
    }

    private String members() {
        StringBuilder result = new StringBuilder();
        for (MemberSummary member : library.members()) {
            result.append(member.name()).append(": ").append(member.loans())
                    .append(member.loans() == 1 ? " loan" : " loans")
                    .append(", owes ").append(member.debt()).append('\n');
        }
        return result.toString().stripTrailing();
    }

    private String reservations() {
        if (library.reservations().isEmpty()) {
            return "No reservations.";
        }
        StringBuilder result = new StringBuilder();
        for (ReservationSummary reservation : library.reservations()) {
            result.append(reservation.book()).append(": ").append(reservation.member())
                    .append(", position ").append(reservation.position()).append('\n');
        }
        return result.toString().stripTrailing();
    }

    private String reset() {
        reset.run();
        return "The library is back in its starting state.";
    }

    private String help() {
        return """
                day 2 March              set the date (the year is 2026)
                borrow John C1           a member borrows a copy
                return C1                a copy comes back
                renew C1                 the loan of a copy is renewed
                reserve Mary The Hobbit  a member reserves a book
                pay Jane                 a member pays what they owe
                copies, members, reservations
                reset, help, quit""";
    }

    // ---- the shape of a line ----

    private static String oneWord(String rest, String example) {
        if (rest.isEmpty() || rest.contains(" ")) {
            throw new IllegalArgumentException("Write it as in \"" + example + "\".");
        }
        return rest;
    }

    private static String[] twoWords(String rest, String example) {
        String[] words = rest.split("\\s+", 2);
        if (words.length < 2) {
            throw new IllegalArgumentException("Write it as in \"" + example + "\".");
        }
        return words;
    }
}
