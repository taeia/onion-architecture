package library.http;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
 * The library over HTTP. A driving adapter: it turns a request into a call on one of
 * the four driving ports and answers with the sentence the window and the command line
 * would print. {@code POST /borrow?member=John&copy=C1}, {@code POST /return?copy=C1},
 * {@code POST /renew?copy=C1}, {@code POST /reserve?member=Mary&book=The+Hobbit}, and
 * {@code POST /pay?member=Jane} are the five commands; {@code GET /copies},
 * {@code GET /members}, and {@code GET /reservations} are the listings. A refusal
 * answers 409 with the reason. There is no {@code day}: this driver does not own the
 * clock, the window it runs beside does.
 *
 * It has no {@code main}. The desktop configurator starts it next to the window and
 * tells it which thread to answer on, so that the window and the terminal drive one
 * library and never at the same instant.
 */
public class HttpApi {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);

    private final ForLendingCopies lending;
    private final ForReservingBooks reserving;
    private final ForPayingFines paying;
    private final ForBrowsingTheLibrary library;
    private HttpServer server;

    public HttpApi(ForLendingCopies lending, ForReservingBooks reserving, ForPayingFines paying,
                   ForBrowsingTheLibrary library) {
        this.lending = lending;
        this.reserving = reserving;
        this.paying = paying;
        this.library = library;
    }

    /**
     * Starts listening on the port, 0 for any free one, and answers every request on
     * the executor, or on the server's own thread when the executor is null.
     */
    public void start(int port, Executor runOn) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(runOn);
        server.createContext("/", this::handle);
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    /**
     * Turns one request into one call on one port, the way {@code Cli.execute} turns
     * one line into one. The method and the path together name the command.
     */
    private void handle(HttpExchange exchange) throws IOException {
        String request = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
        try {
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            reply(exchange, 200, switch (request) {
                case "POST /borrow" -> borrow(query);
                case "POST /return" -> returnCopy(query);
                case "POST /renew" -> renew(query);
                case "POST /reserve" -> reserve(query);
                case "POST /pay" -> pay(query);
                case "GET /copies" -> copies();
                case "GET /members" -> members();
                case "GET /reservations" -> reservations();
                default -> throw new NoSuchElementException("There is nothing at " + request + ".");
            });
        } catch (Refusal e) {
            reply(exchange, 409, "The library refuses. " + e.getMessage());
        } catch (IllegalArgumentException e) {
            reply(exchange, 400, e.getMessage());
        } catch (NoSuchElementException e) {
            reply(exchange, 404, e.getMessage());
        }
    }

    // ---- the five commands ----

    private String borrow(Map<String, String> query) {
        String member = required(query, "member");
        String copy = required(query, "copy");
        Borrowed borrowed = lending.borrow(member, copy);
        return member + " borrowed " + copy + " of " + borrowed.book()
                + ". It is due on " + borrowed.dueDate().format(DAY) + ".";
    }

    private String returnCopy(Map<String, String> query) {
        String copy = required(query, "copy");
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

    private String renew(Map<String, String> query) {
        String copy = required(query, "copy");
        Renewed renewed = lending.renew(copy);
        return renewed.member() + " renewed the loan of " + copy
                + ". It is now due on " + renewed.dueDate().format(DAY) + ".";
    }

    private String reserve(Map<String, String> query) {
        String member = required(query, "member");
        String book = required(query, "book");
        int position = reserving.reserve(member, book);
        return member + " reserved " + book + ". Position in the queue: " + position + ".";
    }

    private String pay(Map<String, String> query) {
        String member = required(query, "member");
        return member + " paid " + paying.pay(member) + " and owes nothing now.";
    }

    // ---- the three listings ----

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
        return result.toString();
    }

    private String members() {
        StringBuilder result = new StringBuilder();
        for (MemberSummary member : library.members()) {
            result.append(member.name()).append(": ").append(member.loans())
                    .append(member.loans() == 1 ? " loan" : " loans")
                    .append(", owes ").append(member.debt()).append('\n');
        }
        return result.toString();
    }

    private String reservations() {
        if (library.reservations().isEmpty()) {
            return "No reservations.\n";
        }
        StringBuilder result = new StringBuilder();
        for (ReservationSummary reservation : library.reservations()) {
            result.append(reservation.book()).append(": ").append(reservation.member())
                    .append(", position ").append(reservation.position()).append('\n');
        }
        return result.toString();
    }

    // ---- the request and the response ----

    private static Map<String, String> query(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery != null) {
            for (String pair : rawQuery.split("&")) {
                int equals = pair.indexOf('=');
                if (equals > 0) {
                    result.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return result;
    }

    private static String required(Map<String, String> query, String name) {
        String value = query.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The request needs a " + name + ".");
        }
        return value;
    }

    private static void reply(HttpExchange exchange, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
