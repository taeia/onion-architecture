package library.desktop;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import library.application.CopySummary;
import library.application.ForBrowsingTheLibrary;
import library.application.ForLendingCopies;
import library.application.Refusal;
import library.application.Returned;

public class ReturnDialog extends Stage {

    public String result = "";

    public ReturnDialog(ForLendingCopies lending, ForBrowsingTheLibrary library) {
        setTitle("Return a copy");
        initModality(Modality.APPLICATION_MODAL);

        Chooser<CopySummary> loanBox = new Chooser<>(Loans.of(library), Loans::describe);
        loanBox.setPrefWidth(320);
        Label message = new Label();
        message.setStyle("-fx-text-fill: red;");

        Button returnButton = new Button("Return");
        returnButton.setOnAction(e -> {
            CopySummary copy = loanBox.getValue();
            if (copy == null) {
                message.setText("Choose a loan.");
                return;
            }
            try {
                Returned returned = lending.returnCopy(copy.number());
                if (returned.daysLate() > 0) {
                    result = returned.member() + " returned " + copy.number() + " " + returned.daysLate()
                            + " days late and now owes " + returned.debt() + ".";
                } else {
                    result = returned.member() + " returned " + copy.number() + " on time.";
                }
                if (returned.heldFor() != null) {
                    result += " " + copy.number() + " is on hold for " + returned.heldFor() + ", who has been notified.";
                }
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
        grid.add(new Label("Loan"), 0, 0);
        grid.add(loanBox, 1, 0);
        grid.add(message, 0, 1, 2, 1);
        grid.add(new HBox(10, returnButton, cancel), 0, 2, 2, 1);

        setScene(new Scene(grid));
    }
}
