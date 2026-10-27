package library.desktop;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import library.application.ForBrowsingTheLibrary;
import library.application.ForPayingFines;
import library.application.MemberSummary;
import library.application.Refusal;

public class PayDialog extends Stage {

    public String result = "";

    public PayDialog(ForPayingFines paying, ForBrowsingTheLibrary library) {
        setTitle("Pay fines");
        initModality(Modality.APPLICATION_MODAL);

        Chooser<MemberSummary> memberBox = new Chooser<>(library.members(), MemberSummary::name);
        Label message = new Label();
        message.setStyle("-fx-text-fill: red;");

        Button pay = new Button("Pay");
        pay.setOnAction(e -> {
            MemberSummary member = memberBox.getValue();
            if (member == null) {
                message.setText("Choose a member.");
                return;
            }
            try {
                result = member.name() + " paid " + paying.pay(member.name()) + " and owes nothing now.";
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
        grid.add(message, 0, 1, 2, 1);
        grid.add(new HBox(10, pay, cancel), 0, 2, 2, 1);

        setScene(new Scene(grid));
    }
}
