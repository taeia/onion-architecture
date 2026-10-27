package library.desktop;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import library.application.BookSummary;
import library.application.ForBrowsingTheLibrary;
import library.application.ForReservingBooks;
import library.application.MemberSummary;
import library.application.Refusal;

public class ReserveDialog extends Stage {

    public String result = "";

    public ReserveDialog(ForReservingBooks reserving, ForBrowsingTheLibrary library) {
        setTitle("Reserve a book");
        initModality(Modality.APPLICATION_MODAL);

        Chooser<MemberSummary> memberBox = new Chooser<>(library.members(), MemberSummary::name);
        Chooser<BookSummary> bookBox = new Chooser<>(library.books(), BookSummary::title);
        Label message = new Label();
        message.setStyle("-fx-text-fill: red;");

        Button reserve = new Button("Reserve");
        reserve.setOnAction(e -> {
            MemberSummary member = memberBox.getValue();
            BookSummary book = bookBox.getValue();
            if (member == null || book == null) {
                message.setText("Choose a member and a book.");
                return;
            }
            try {
                int position = reserving.reserve(member.name(), book.title());
                result = member.name() + " reserved " + book.title() + ". Position in the queue: " + position + ".";
                close();
            } catch (Refusal ex) {
                message.setText(ex.getMessage());
            }
        });

        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> close());

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(15));
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Member"), 0, 0);
        grid.add(memberBox, 1, 0);
        grid.add(new Label("Book"), 0, 1);
        grid.add(bookBox, 1, 1);
        grid.add(message, 0, 2, 2, 1);
        grid.add(new HBox(10, reserve, cancel), 0, 3, 2, 1);

        setScene(new Scene(grid));
    }
}
