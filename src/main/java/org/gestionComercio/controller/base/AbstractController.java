package org.gestionComercio.controller.base;

import javafx.fxml.Initializable;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.util.AlertUtils;

import java.net.URL;
import java.util.ResourceBundle;

public abstract class AbstractController
        implements Initializable, ViewController {

    @Override
    public final void initialize(URL location,
                                 ResourceBundle resources) {

        initializeComponents();
        initializeBindings();
        initializeEvents();
    }

    protected void initializeComponents(){}

    protected void initializeBindings(){}

    protected void initializeEvents(){}

    protected void info(String mensaje){
        AlertUtils.information("Información", mensaje);
    }

    protected void warning(String mensaje){
        AlertUtils.warning("Atención", mensaje);
    }

    protected void error(String mensaje){
        AlertUtils.error("Error", mensaje);
    }

}