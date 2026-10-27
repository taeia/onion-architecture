package library.desktop;

import java.util.List;
import java.util.function.Function;

import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

/**
 * A combo box that shows each item by the text a function makes of it. The records
 * that come through the ports have no idea how they should look in a list, and that
 * is right: deciding it is the adapter's job, and this is where the adapter does it.
 */
class Chooser<T> extends ComboBox<T> {

    Chooser(List<T> items, Function<T, String> text) {
        super(FXCollections.observableArrayList(items));
        setConverter(new StringConverter<>() {
            @Override
            public String toString(T item) {
                return item == null ? "" : text.apply(item);
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        });
    }
}
