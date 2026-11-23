package org.gestionComercio.service.impl;

import jakarta.persistence.EntityManager;
import org.gestionComercio.config.AuditorProvider;
import org.gestionComercio.config.JpaAuditingConfig;
import org.gestionComercio.dto.producto.ActualizacionPrecioDto;
import org.gestionComercio.dto.producto.FiltroActualizacionPrecios;
import org.gestionComercio.dto.producto.PrecioActualizadoDto;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.entity.Empresa;
import org.gestionComercio.entity.Marca;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.entity.Rol;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.PrecioActualizado;
import org.gestionComercio.enums.Redondeo;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ActualizacionPreciosService;
import org.gestionComercio.support.PostgresTestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Actualización masiva de precios contra un PostgreSQL real: filtros, vista
 * previa, aplicación y registro de auditoría.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        PostgresTestcontainersConfig.class,
        ActualizacionPreciosServiceImpl.class,
        SesionUsuario.class,
        JpaAuditingConfig.class,
        AuditorProvider.class
})
class ActualizacionPreciosIntegrationTest {

    private static final AtomicLong SECUENCIA = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    @Autowired
    private ActualizacionPreciosService servicio;

    @Autowired
    private SesionUsuario sesionUsuario;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaccion;

    private String sufijo;
    private Usuario usuario;
    private Categoria bebidas;
    private Categoria almacen;
    private Marca marca;
    private Proveedor proveedor;

    @BeforeEach
    void setUp() {
        transaccion = new TransactionTemplate(transactionManager);
        sesionUsuario.cerrarSesion();
        sufijo = String.valueOf(SECUENCIA.incrementAndGet());

        usuario = transaccion.execute(status -> {
            Empresa empresa = Empresa.builder()
                    .razonSocial("Empresa " + sufijo).cuit("31" + sufijo)
                    .condicionIva(CondicionIVA.RESPONSABLE_INSCRIPTO)
                    .build();
            Rol rol = Rol.builder().nombre("ROLP" + sufijo).build();
            Usuario u = Usuario.builder()
                    .usuario("precios" + sufijo).password("x").nombre("Luis").apellido("García")
                    .empresa(empresa).rol(rol).tipoDocumento(TipoDocumento.DNI).documento("9" + sufijo)
                    .build();
            bebidas = Categoria.builder().nombre("Bebidas " + sufijo).build();
            almacen = Categoria.builder().nombre("Almacén " + sufijo).build();
            marca = Marca.builder().nombre("Marca " + sufijo).build();
            proveedor = Proveedor.builder().razonSocial("Proveedor " + sufijo).build();
            entityManager.persist(empresa);
            entityManager.persist(rol);
            entityManager.persist(u);
            entityManager.persist(bebidas);
            entityManager.persist(almacen);
            entityManager.persist(marca);
            entityManager.persist(proveedor);
            return u;
        });

        sesionUsuario.iniciarSesion(usuario, EnumSet.allOf(PermisoCodigo.class));
    }

    private Long crearProducto(String nombre, Categoria categoria, String compra, String venta, EstadoRegistro estado) {
        return transaccion.execute(status -> {
            Producto producto = Producto.builder()
                    .codigo(nombre + sufijo).nombre(nombre + " " + sufijo)
                    .precioCompra(new BigDecimal(compra))
                    .precioVenta(new BigDecimal(venta))
                    .categoria(entityManager.merge(categoria))
                    .marca(entityManager.merge(marca))
                    .proveedor(entityManager.merge(proveedor))
                    .build();
            producto.setEstadoRegistro(estado);
            entityManager.persist(producto);
            return producto.getId();
        });
    }

    private Producto producto(Long id) {
        return transaccion.execute(status -> entityManager.find(Producto.class, id));
    }

    private FiltroActualizacionPrecios bebidas(String porcentaje, PrecioActualizado precio, Redondeo redondeo) {
        return new FiltroActualizacionPrecios(new BigDecimal(porcentaje), precio, redondeo, bebidas.getId(), null, null);
    }

    @Test
    @DisplayName("La vista previa calcula los precios nuevos de los productos activos del filtro sin guardar nada")
    void vistaPrevia() {
        Long cola = crearProducto("Cola", bebidas, "800.00", "1000.00", EstadoRegistro.ACTIVO);
        crearProducto("Agua", bebidas, "300.00", "500.00", EstadoRegistro.INACTIVO);
        crearProducto("Yerba", almacen, "2000.00", "3000.00", EstadoRegistro.ACTIVO);

        List<PrecioActualizadoDto> vista = servicio.previsualizar(bebidas("15", PrecioActualizado.VENTA, null));

        assertThat(vista).singleElement().satisfies(p -> {
            assertThat(p.productoId()).isEqualTo(cola);
            assertThat(p.precioVentaAnterior()).isEqualByComparingTo("1000");
            assertThat(p.precioVentaNuevo()).isEqualByComparingTo("1150");
            assertThat(p.precioCompraNuevo()).isEqualByComparingTo("800");
        });

        assertThat(producto(cola).getPrecioVenta()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Aplicar actualiza los precios, redondea y deja el registro con usuario, criterio y precios anteriores")
    void aplicar() {
        Long cola = crearProducto("Cola", bebidas, "800.00", "1003.00", EstadoRegistro.ACTIVO);
        Long jugo = crearProducto("Jugo", bebidas, "3.00", "4.00", EstadoRegistro.ACTIVO);
        Long yerba = crearProducto("Yerba", almacen, "2000.00", "3000.00", EstadoRegistro.ACTIVO);

        FiltroActualizacionPrecios filtro = bebidas("10", PrecioActualizado.AMBOS, Redondeo.DIEZ_PESOS);

        ActualizacionPrecioDto registro = servicio.aplicar(filtro, servicio.previsualizar(filtro));

        // 1003 × 1,10 = 1103,30 → 1100; 800 × 1,10 = 880; 4 × 1,10 = 4,40 → nunca a cero: 10
        assertThat(producto(cola).getPrecioVenta()).isEqualByComparingTo("1100");
        assertThat(producto(cola).getPrecioCompra()).isEqualByComparingTo("880");
        assertThat(producto(jugo).getPrecioVenta()).isEqualByComparingTo("10");
        assertThat(producto(yerba).getPrecioVenta()).isEqualByComparingTo("3000");

        assertThat(registro.cantidadProductos()).isEqualTo(2);
        assertThat(registro.usuario()).isEqualTo("Luis García");
        assertThat(registro.filtro()).isEqualTo("Categoría: " + bebidas.getNombre());
        assertThat(registro.fecha()).isNotNull();

        assertThat(servicio.historial()).first().satisfies(a -> {
            assertThat(a.id()).isEqualTo(registro.id());
            assertThat(a.porcentaje()).isEqualByComparingTo("10");
            assertThat(a.precio()).isEqualTo(PrecioActualizado.AMBOS);
            assertThat(a.redondeo()).isEqualTo(Redondeo.DIEZ_PESOS);
        });

        assertThat(servicio.detalle(registro.id()))
                .extracting(PrecioActualizadoDto::productoId, p -> p.precioVentaAnterior().toPlainString(),
                        p -> p.precioVentaNuevo().toPlainString())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(cola, "1003.00", "1100.00"),
                        org.assertj.core.groups.Tuple.tuple(jugo, "4.00", "10.00"));
    }

    @Test
    @DisplayName("Si un precio cambió después de la vista previa, no se aplica nada")
    void vistaPreviaVieja() {
        Long cola = crearProducto("Cola", bebidas, "800.00", "1000.00", EstadoRegistro.ACTIVO);
        Long jugo = crearProducto("Jugo", bebidas, "500.00", "700.00", EstadoRegistro.ACTIVO);

        FiltroActualizacionPrecios filtro = bebidas("20", PrecioActualizado.VENTA, null);
        List<PrecioActualizadoDto> vista = servicio.previsualizar(filtro);

        transaccion.executeWithoutResult(status ->
                entityManager.find(Producto.class, jugo).setPrecioVenta(new BigDecimal("750.00")));

        assertThatThrownBy(() -> servicio.aplicar(filtro, vista))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vista previa");

        assertThat(producto(cola).getPrecioVenta()).isEqualByComparingTo("1000");
        assertThat(producto(jugo).getPrecioVenta()).isEqualByComparingTo("750");
    }

    @Test
    @DisplayName("Valida el porcentaje y requiere PRODUCTO_ACTUALIZAR_PRECIOS")
    void validaciones() {
        crearProducto("Cola", bebidas, "800.00", "1000.00", EstadoRegistro.ACTIVO);

        for (String porcentaje : List.of("0", "-100", "1000.01", "1.234")) {
            assertThatThrownBy(() -> servicio.previsualizar(bebidas(porcentaje, PrecioActualizado.VENTA, null)))
                    .as(porcentaje)
                    .isInstanceOf(ValidationException.class);
        }

        assertThat(servicio.previsualizar(bebidas("-99.5", PrecioActualizado.VENTA, null)))
                .singleElement()
                .satisfies(p -> assertThat(p.precioVentaNuevo()).isEqualByComparingTo("5"));

        sesionUsuario.iniciarSesion(usuario, EnumSet.of(PermisoCodigo.PRODUCTO_EDITAR));

        assertThatThrownBy(() -> servicio.previsualizar(bebidas("10", PrecioActualizado.VENTA, null)))
                .isInstanceOf(AuthorizationException.class);
    }
}
