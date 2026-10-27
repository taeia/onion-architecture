package library.desktop;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import library.application.BrowsingService;
import library.application.LoanService;
import library.application.MemberService;
import library.application.ReservationService;
import library.domain.Lending;
import library.domain.Reserving;
import library.http.HttpApi;
import library.inmemory.InMemoryBookRepository;
import library.inmemory.InMemoryCopyRepository;
import library.inmemory.InMemoryMemberRepository;
import library.inmemory.InMemoryNotifier;
import library.inmemory.InMemoryQueueRepository;
import library.inmemory.SettableClock;
import library.inmemory.StartingState;
import library.application.ChangeSignal;
import library.application.CopySummary;
import library.application.ForBrowsingTheLibrary;
import library.application.ForLendingCopies;
import library.application.ForPayingFines;
import library.application.ForReservingBooks;
import library.application.MemberSummary;
import library.application.ReservationSummary;

public class MainWindow extends Application {

    private static final int PORT = 8080;

    private ForLendingCopies loanService;
    private ForReservingBooks reservationService;
    private ForPayingFines memberService;
    private ForBrowsingTheLibrary library;
    private InMemoryNotifier notifier;
    private HttpApi api;

    private final ObservableList<CopySummary> copies = FXCollections.observableArrayList();
    private final ObservableList<MemberSummary> members = FXCollections.observableArrayList();
    private final ObservableList<ReservationSummary> reservations = FXCollections.observableArrayList();
    private final ObservableList<String> notifications = FXCollections.observableArrayList();
    private Label status;

    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        // The configurator for this program: the driven adapters, then the domain
        // services that use them, then the application services, which the rest of the
        // window holds as driving ports. One of the driven adapters is the window
        // itself: the change signal is a lambda that refreshes the tables, on the
        // window's own thread whatever thread it was rung from.
        InMemoryBookRepository bookRepository = new InMemoryBookRepository();
        InMemoryCopyRepository copyRepository = new InMemoryCopyRepository();
        InMemoryMemberRepository memberRepository = new InMemoryMemberRepository();
        InMemoryQueueRepository queueRepository = new InMemoryQueueRepository();
        SettableClock clock = new SettableClock();
        notifier = new InMemoryNotifier();
        ChangeSignal signal = () -> Platform.runLater(this::refresh);
        Lending lending = new Lending(memberRepository, copyRepository, queueRepository, clock);
        Reserving reserving = new Reserving(copyRepository, queueRepository);
        loanService = new LoanService(lending, memberRepository, copyRepository, notifier, signal);
        reservationService = new ReservationService(reserving, bookRepository, memberRepository, queueRepository, signal);
        memberService = new MemberService(memberRepository, signal);
        library = new BrowsingService(bookRepository, copyRepository, memberRepository, queueRepository);

        // The same configurator starts the second driver of this program: the HTTP
        // endpoint, on the same four ports. Every request is answered on the JavaFX
        // thread, so the window and the terminal never reach the core at the same
        // instant, and the signal a request rings refreshes this window.
        api = new HttpApi(loanService, reservationService, memberService, library);
        String listening;
        try {
            api.start(PORT, Platform::runLater);
            listening = "It also listens on http://localhost:" + PORT + ".";
        } catch (IOException e) {
            api = null;
            listening = "Port " + PORT + " is taken, so it listens to this window only.";
        }

        // The starting state is loaded where the repositories are built. No service owns
        // the whole library any more, so none of them can offer a reset(). The mailbox is
        // this window's, so the window empties it.
        Runnable resetLibrary = () -> {
            StartingState.load(bookRepository, copyRepository, memberRepository, queueRepository);
            notifier.clear();
        };
        resetLibrary.run();

        DatePicker datePicker = new DatePicker(clock.today());
        datePicker.setOnAction(e -> {
            clock.set(datePicker.getValue());
            refresh();
        });

        Button borrow = new Button("Borrow");
        borrow.setOnAction(e -> {
            BorrowDialog dialog = new BorrowDialog(loanService, library);
            dialog.showAndWait();
            done(dialog.result);
        });
        Button returnButton = new Button("Return");
        returnButton.setOnAction(e -> {
            ReturnDialog dialog = new ReturnDialog(loanService, library);
            dialog.showAndWait();
            done(dialog.result);
        });
        Button renew = new Button("Renew");
        renew.setOnAction(e -> {
            RenewDialog dialog = new RenewDialog(loanService, library);
            dialog.showAndWait();
            done(dialog.result);
        });
        Button reserve = new Button("Reserve");
        reserve.setOnAction(e -> {
            ReserveDialog dialog = new ReserveDialog(reservationService, library);
            dialog.showAndWait();
            done(dialog.result);
        });
        Button pay = new Button("Pay");
        pay.setOnAction(e -> {
            PayDialog dialog = new PayDialog(memberService, library);
            dialog.showAndWait();
            done(dialog.result);
        });
        Button reset = new Button("Reset library");
        reset.setOnAction(e -> {
            resetLibrary.run();
            status.setText("The library is back in its starting state.");
            refresh();
        });

        HBox toolbar = new HBox(10, new Label("Today:"), datePicker, borrow, returnButton, renew, reserve, pay, reset);
        toolbar.setPadding(new Insets(10));

        TableView<CopySummary> copyTable = new TableView<>(copies);
        TableColumn<CopySummary, String> copyColumn = new TableColumn<>("Copy");
        copyColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().number()));
        TableColumn<CopySummary, String> bookColumn = new TableColumn<>("Book");
        bookColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().book()));
        bookColumn.setPrefWidth(160);
        TableColumn<CopySummary, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(switch (c.getValue().status()) {
            case AVAILABLE -> "available";
            case ON_LOAN -> "on loan";
            case ON_HOLD -> "on hold for " + c.getValue().heldFor();
        }));
        statusColumn.setPrefWidth(120);
        TableColumn<CopySummary, String> borrowerColumn = new TableColumn<>("Borrower");
        borrowerColumn.setCellValueFactory(c -> {
            String borrower = c.getValue().borrower();
            return new ReadOnlyStringWrapper(borrower == null ? "" : borrower);
        });
        TableColumn<CopySummary, String> dueColumn = new TableColumn<>("Due");
        dueColumn.setCellValueFactory(c -> {
            CopySummary copy = c.getValue();
            return new ReadOnlyStringWrapper(copy.dueDate() == null ? "" : copy.dueDate().format(DateTimeFormatter.ofPattern("d MMMM")));
        });
        copyTable.getColumns().add(copyColumn);
        copyTable.getColumns().add(bookColumn);
        copyTable.getColumns().add(statusColumn);
        copyTable.getColumns().add(borrowerColumn);
        copyTable.getColumns().add(dueColumn);

        TableView<MemberSummary> memberTable = new TableView<>(members);
        TableColumn<MemberSummary, String> nameColumn = new TableColumn<>("Member");
        nameColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().name()));
        TableColumn<MemberSummary, String> loansColumn = new TableColumn<>("Loans");
        loansColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().loans())));
        TableColumn<MemberSummary, String> debtColumn = new TableColumn<>("Debt");
        debtColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().debt().toString()));
        memberTable.getColumns().add(nameColumn);
        memberTable.getColumns().add(loansColumn);
        memberTable.getColumns().add(debtColumn);

        TableView<ReservationSummary> reservationTable = new TableView<>(reservations);
        TableColumn<ReservationSummary, String> reservedBookColumn = new TableColumn<>("Book");
        reservedBookColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().book()));
        reservedBookColumn.setPrefWidth(160);
        TableColumn<ReservationSummary, String> reservedByColumn = new TableColumn<>("Member");
        reservedByColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().member()));
        TableColumn<ReservationSummary, String> positionColumn = new TableColumn<>("Position");
        positionColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().position())));
        reservationTable.getColumns().add(reservedBookColumn);
        reservationTable.getColumns().add(reservedByColumn);
        reservationTable.getColumns().add(positionColumn);

        ListView<String> notificationList = new ListView<>(notifications);

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(titled("Copies", copyTable), 0, 0);
        grid.add(titled("Members", memberTable), 1, 0);
        grid.add(titled("Reservations", reservationTable), 0, 1);
        grid.add(titled("Notifications", notificationList), 1, 1);

        status = new Label("Welcome to the library. " + listening);
        status.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(grid);
        root.setBottom(status);

        refresh();
        stage.setTitle("Library");
        stage.setScene(new Scene(root, 1000, 600));
        stage.show();
    }

    @Override
    public void stop() {
        if (api != null) {
            api.stop();
        }
    }

    /**
     * A dialog's sentence goes to the status bar. The tables are not refreshed here:
     * the command the dialog ran has rung the signal, and the signal refreshes them.
     */
    private void done(String result) {
        status.setText(result);
    }

    private VBox titled(String title, Node content) {
        Label label = new Label(title);
        label.setStyle("-fx-font-weight: bold;");
        VBox box = new VBox(5, label, content);
        VBox.setVgrow(content, Priority.ALWAYS);
        return box;
    }

    /**
     * Asks the library everything again. This is the whole of what the window does
     * when the signal rings, whoever rang it: the signal says nothing about what
     * changed, so the window does not guess.
     */
    private void refresh() {
        copies.setAll(library.copies());
        members.setAll(library.members());
        reservations.setAll(library.reservations());
        notifications.setAll(notifier.sent());
    }
}
