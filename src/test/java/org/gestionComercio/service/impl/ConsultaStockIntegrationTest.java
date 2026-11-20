package org.gestionComercio.service.impl;

import jakarta.persistence.EntityManager;
import org.gestionComercio.config.AuditorProvider;
import org.gestionComercio.config.JpaAuditingConfig;
import org.gestionComercio.dto.inventario.ResumenStock;
import org.gestionComercio.dto.inventario.StockProductoDto;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.entity.Marca;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.FiltroStock;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.SituacionStock;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ConsultaStockService;
import org.gestionComercio.support.PostgresTestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba las consultas JPQL de stock contra un PostgreSQL real (Testcontainers), para
 * verificar que las comparaciones de cantidades (decimales, negativos, stock
 * contra mínimo) y los filtros funcionan igual que {@link SituacionStock}.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        PostgresTestcontainersConfig.class,
        ConsultaStockServiceImpl.class,
        SesionUsuario.class,
        JpaAuditingConfig.class,
        AuditorProvider.class
})
class ConsultaStockIntegrationTest {

    @Autowired
    private ConsultaStockService consultaStockService;

    @Autowired
    private SesionUsuario sesionUsuario;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Categoria almacen;
    private Categoria bebidas;
    private Marca marca;
    private Proveedor proveedor;

    @BeforeEach
    void setUp() {
        // Cada test corre en su propia transacción (que se revierte), pero la
        // base la comparten todos los tests de la clase: se arranca siempre de cero.
        jdbcTemplate.update("delete from productos");

        // Sin sesión mientras se cargan los datos: la auditoría registraría como
        // autor a un usuario que no está guardado.
        sesionUsuario.cerrarSesion();

        almacen = Categoria.builder().nombre("Almacén").build();
        bebidas = Categoria.builder().nombre("Bebidas").build();
        marca = Marca.builder().nombre("Marca").build();
        proveedor = Proveedor.builder().razonSocial("Proveedor").build();
        entityManager.persist(almacen);
        entityManager.persist(bebidas);
        entityManager.persist(marca);
        entityManager.persist(proveedor);

        crear("Arroz", almacen, "20", "5", true);          // normal
        crear("Azúcar", almacen, "5", "5", true);          // stock bajo (en el mínimo)
        crear("Queso", almacen, "0.250", "1", true);        // stock bajo (decimales)
        crear("Harina", almacen, "0", "3", true);          // sin stock
        crear("Yerba", almacen, "-2", "0", true);          // sin stock (negativo)
        crear("Agua", bebidas, "0", "0", true);            // sin stock (mínimo cero)
        crear("Gaseosa", bebidas, "12", "4", true);         // normal
        crear("Envío", bebidas, "0", "0", false);          // no maneja stock

        Producto dadoDeBaja = crear("Vino", bebidas, "0", "10", true);
        dadoDeBaja.setEstadoRegistro(EstadoRegistro.INACTIVO);

        entityManager.flush();
        entityManager.clear();

        sesionUsuario.iniciarSesion(
                Usuario.builder().usuario("ana").nombre("Ana").build(),
                EnumSet.of(PermisoCodigo.INVENTARIO_VER));
    }

    private Producto crear(String nombre, Categoria categoria, String stock, String minimo, boolean manejaStock) {
        Producto producto = Producto.builder()
                .codigo(nombre.toUpperCase())
                .nombre(nombre)
                .unidadMedida(UnidadMedida.KILOGRAMO)
                .stock(new BigDecimal(stock))
                .stockMinimo(new BigDecimal(minimo))
                .manejaStock(manejaStock)
                // La base sólo admite stock negativo si el producto lo permite.
                .permiteStockNegativo(new BigDecimal(stock).signum() < 0)
                .categoria(categoria)
                .marca(marca)
                .proveedor(proveedor)
                .estadoRegistro(EstadoRegistro.ACTIVO)
                .build();
        entityManager.persist(producto);
        return producto;
    }

    private static List<String> nombres(List<StockProductoDto> productos) {
        return productos.stream().map(StockProductoDto::nombre).toList();
    }

    @Test
    @DisplayName("Sin filtros muestra el stock de todos los productos activos, ordenados por nombre")
    void todosLosActivos() {
        List<StockProductoDto> productos = consultaStockService.consultar("", null, FiltroStock.TODOS);

        assertThat(nombres(productos)).containsExactly(
                "Agua", "Arroz", "Azúcar", "Envío", "Gaseosa", "Harina", "Queso", "Yerba");

        StockProductoDto queso = productos.stream().filter(p -> p.nombre().equals("Queso")).findFirst().orElseThrow();
        assertThat(queso.stock()).isEqualByComparingTo("0.250");
        assertThat(queso.stockMinimo()).isEqualByComparingTo("1");
        assertThat(queso.categoriaNombre()).isEqualTo("Almacén");
        assertThat(queso.marcaNombre()).isEqualTo("Marca");
        assertThat(queso.situacion()).isEqualTo(SituacionStock.STOCK_BAJO);
    }

    @Test
    @DisplayName("El filtro de stock bajo trae los que están en el mínimo o por debajo, incluidos los sin stock")
    void stockBajo() {
        List<StockProductoDto> productos = consultaStockService.consultar("", null, FiltroStock.STOCK_BAJO);

        assertThat(nombres(productos)).containsExactly("Agua", "Azúcar", "Harina", "Queso", "Yerba");
        assertThat(productos).allMatch(p -> p.situacion().requiereReposicion());
    }

    @Test
    @DisplayName("El filtro sin stock trae los que tienen stock en cero o negativo")
    void sinStock() {
        List<StockProductoDto> productos = consultaStockService.consultar("", null, FiltroStock.SIN_STOCK);

        assertThat(nombres(productos)).containsExactly("Agua", "Harina", "Yerba");
        assertThat(productos).allMatch(p -> p.situacion() == SituacionStock.SIN_STOCK);
    }

    @Test
    @DisplayName("Filtra por categoría, combinable con la situación y el texto")
    void porCategoria() {
        assertThat(nombres(consultaStockService.consultar("", bebidas.getId(), FiltroStock.TODOS)))
                .containsExactly("Agua", "Envío", "Gaseosa");

        assertThat(nombres(consultaStockService.consultar("", almacen.getId(), FiltroStock.SIN_STOCK)))
                .containsExactly("Harina", "Yerba");

        assertThat(nombres(consultaStockService.consultar("ar", almacen.getId(), FiltroStock.TODOS)))
                .containsExactly("Arroz", "Azúcar", "Harina");
    }

    @Test
    @DisplayName("Consulta el stock de un producto por id, aunque esté dado de baja")
    void porProducto() {
        Long idVino = entityManager
                .createQuery("select p.id from Producto p where p.codigo = 'VINO'", Long.class)
                .getSingleResult();

        StockProductoDto vino = consultaStockService.consultarProducto(idVino);

        assertThat(vino.nombre()).isEqualTo("Vino");
        assertThat(vino.isActivo()).isFalse();
        assertThat(vino.situacion()).isEqualTo(SituacionStock.SIN_STOCK);
    }

    @Test
    @DisplayName("El resumen cuenta sólo productos activos que manejan stock")
    void resumen() {
        assertThat(consultaStockService.resumen()).isEqualTo(new ResumenStock(7, 5, 3));
    }
}
