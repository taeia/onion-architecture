package library.desktop;

import java.time.format.DateTimeFormatter;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import library.application.Borrowed;
import library.application.CopySummary;
import library.application.ForBrowsingTheLibrary;
import library.application.ForLendingCopies;
import library.application.MemberSummary;
import library.application.Refusal;

public class BorrowDialog extends Stage {

    public String result = "";

    public BorrowDialog(ForLendingCopies lending, ForBrowsingTheLibrary library) {
        setTitle("Borrow a copy");
        initModality(Modality.APPLICATION_MODAL);

        Chooser<MemberSummary> memberBox = new Chooser<>(library.members(), MemberSummary::name);
        Chooser<CopySummary> copyBox = new Chooser<>(library.copies(), c -> c.number() + " (" + c.book() + ")");
        Label message = new Label();
        message.setStyle("-fx-text-fill: red;");

        Button borrow = new Button("Borrow");
        borrow.setOnAction(e -> {
            MemberSummary member = memberBox.getValue();
            CopySummary copy = copyBox.getValue();
            if (member == null || copy == null) {
                message.setText("Choose a member and a copy.");
                return;
            }
            try {
                Borrowed borrowed = lending.borrow(member.name(), copy.number());
                result = member.name() + " borrowed " + copy.number() + " of " + borrowed.book()
                        + ". It is due on " + borrowed.dueDate().format(DateTimeFormatter.ofPattern("d MMMM")) + ".";
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
        grid.add(new Label("Copy"), 0, 1);
        grid.add(copyBox, 1, 1);
        grid.add(message, 0, 2, 2, 1);
        grid.add(new HBox(10, borrow, cancel), 0, 3, 2, 1);

        setScene(new Scene(grid));
    }
}
