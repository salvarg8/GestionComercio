package org.gestionComercio.navigation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.enums.NavigationType;

@Getter
@RequiredArgsConstructor
public enum AppView {

    LOGIN(
            "/org/gestionComercio/view/login/LoginView.fxml",
            "Iniciar sesión",
            NavigationType.WINDOW,
            WindowMode.AUTO_SIZE
    ),

    DASHBOARD(
            "/org/gestionComercio/view/dashboard/DashboardView.fxml",
            "Gestión Comercial",
            NavigationType.WINDOW,
            WindowMode.MAXIMIZED
    );

    private final String fxml;

    private final String titulo;

    private final NavigationType navigationType;

    private final WindowMode windowMode;

}