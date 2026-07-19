package org.gestionComercio.navigation;

import javafx.scene.Parent;
import org.gestionComercio.enums.NavigationType;

public record LoadedView(
        AppView view,
        Parent root,
        Object controller
) {

    public String title() {
        return view.getTitulo();
    }

    public NavigationType navigationType() {
        return view.getNavigationType();
    }

}