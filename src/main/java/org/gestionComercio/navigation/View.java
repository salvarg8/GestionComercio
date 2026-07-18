package org.gestionComercio.navigation;

public enum View {

    LOGIN("/fxml/login.fxml", "Iniciar sesión"),

    DASHBOARD("/fxml/dashboard.fxml", "Dashboard"),

    PRODUCTOS("/fxml/producto/productos.fxml", "Productos"),

    CLIENTES("/fxml/cliente/clientes.fxml", "Clientes"),

    PROVEEDORES("/fxml/proveedor/proveedores.fxml", "Proveedores"),

    VENTAS("/fxml/venta/ventas.fxml", "Ventas"),

    COMPRAS("/fxml/compra/compras.fxml", "Compras"),

    CAJA("/fxml/caja/caja.fxml", "Caja");

    private final String fxml;

    private final String titulo;

    View(String fxml, String titulo) {
        this.fxml = fxml;
        this.titulo = titulo;
    }

    public String getFxml() {
        return fxml;
    }

    public String getTitulo() {
        return titulo;
    }


}
