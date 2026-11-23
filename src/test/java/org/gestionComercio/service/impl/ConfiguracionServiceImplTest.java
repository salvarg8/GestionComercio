package org.gestionComercio.service.impl;

import org.gestionComercio.dto.configuracion.ConfiguracionDto;
import org.gestionComercio.dto.configuracion.DatosInicialesResultado;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.entity.Configuracion;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.ComprobantePorDefecto;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.FormatoTicket;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.Redondeo;
import org.gestionComercio.enums.Rubro;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.ConfiguracionMapper;
import org.gestionComercio.mapper.ConfiguracionMapperImpl;
import org.gestionComercio.repository.CategoriaRepository;
import org.gestionComercio.repository.ConfiguracionRepository;
import org.gestionComercio.security.SesionUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfiguracionServiceImplTest {

    @Mock
    private ConfiguracionRepository configuracionRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    private final ConfiguracionMapper mapper = new ConfiguracionMapperImpl();

    private SesionUsuario sesionUsuario;

    private ConfiguracionServiceImpl servicio;

    private Configuracion guardada;

    @BeforeEach
    void setUp() {
        sesionUsuario = new SesionUsuario();
        sesionUsuario.iniciarSesion(new Usuario(), EnumSet.allOf(PermisoCodigo.class));

        servicio = new ConfiguracionServiceImpl(configuracionRepository, categoriaRepository, mapper, sesionUsuario);

        guardada = Configuracion.builder().pieComprobante("Pie viejo").build();
        guardada.setId(1L);

        lenient().when(configuracionRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(guardada));
        lenient().when(configuracionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("vigente() no pide permisos y, si falta la fila, devuelve los valores por defecto")
    void vigente() {
        sesionUsuario.iniciarSesion(new Usuario(), EnumSet.noneOf(PermisoCodigo.class));

        assertThat(servicio.vigente().getPieComprobante()).isEqualTo("Pie viejo");

        when(configuracionRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        ConfiguracionDto defecto = servicio.vigente();
        assertThat(defecto.exigeCaja()).isTrue();
        assertThat(defecto.getFormaPagoPorDefecto()).isEqualTo(FormaPago.EFECTIVO);
        assertThat(defecto.getComprobantePorDefecto()).isEqualTo(ComprobantePorDefecto.FACTURA);
        assertThat(defecto.getRedondeo()).isEqualTo(Redondeo.SIN_REDONDEO);
        assertThat(defecto.getFormatoTicket()).isEqualTo(FormatoTicket.TERMICO_80);
    }

    @Test
    @DisplayName("obtener() requiere CONFIGURACION_VER y actualizar() CONFIGURACION_EDITAR")
    void permisos() {
        sesionUsuario.iniciarSesion(new Usuario(), EnumSet.of(PermisoCodigo.CONFIGURACION_VER));

        assertThat(servicio.obtener()).isNotNull();
        assertThatThrownBy(() -> servicio.actualizar(ConfiguracionDto.porDefecto()))
                .isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> servicio.cargarDatosIniciales(Rubro.KIOSCO))
                .isInstanceOf(AuthorizationException.class);

        sesionUsuario.iniciarSesion(new Usuario(), EnumSet.noneOf(PermisoCodigo.class));
        assertThatThrownBy(() -> servicio.obtener()).isInstanceOf(AuthorizationException.class);

        verify(configuracionRepository, never()).save(any());
    }

    @Test
    @DisplayName("actualizar() completa los valores por defecto, recorta el pie y deja null si queda vacío")
    void actualizarNormaliza() {
        ConfiguracionDto dto = new ConfiguracionDto();
        dto.setVentaExigeCaja(false);
        dto.setPieComprobante("   ");

        ConfiguracionDto resultado = servicio.actualizar(dto);

        assertThat(resultado.exigeCaja()).isFalse();
        assertThat(resultado.getFormaPagoPorDefecto()).isEqualTo(FormaPago.EFECTIVO);
        assertThat(resultado.getUnidadMedidaPorDefecto()).isEqualTo(UnidadMedida.UNIDAD);
        assertThat(resultado.getFormatoTicket()).isEqualTo(FormatoTicket.TERMICO_80);
        assertThat(resultado.getImprimirLogo()).isTrue();
        assertThat(resultado.getPieComprobante()).isNull();
        assertThat(guardada.getPieComprobante()).isNull();
        assertThat(guardada.getVentaExigeCaja()).isFalse();

        dto = ConfiguracionDto.porDefecto();
        dto.setPieComprobante("  Horario: 9 a 20 h\n@comercio  ");
        assertThat(servicio.actualizar(dto).getPieComprobante()).isEqualTo("Horario: 9 a 20 h\n@comercio");
    }

    @Test
    @DisplayName("actualizar() rechaza una forma de pago que no es del punto de venta y un pie demasiado largo")
    void actualizarValida() {
        ConfiguracionDto dto = ConfiguracionDto.porDefecto();
        dto.setFormaPagoPorDefecto(FormaPago.CUENTA_CORRIENTE);
        dto.setPieComprobante("x".repeat(ConfiguracionDto.PIE_MAX + 1));

        assertThatThrownBy(() -> servicio.actualizar(dto))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.getValidationResult().getErrors())
                                .extracting(err -> err.getField())
                                .containsExactlyInAnyOrder("formaPagoPorDefecto", "pieComprobante"));

        verify(configuracionRepository, never()).save(any());
    }

    @Test
    @DisplayName("cargarDatosIniciales() crea sólo las categorías que faltan y fija la unidad del rubro")
    void datosIniciales() {
        when(categoriaRepository.findByNombreIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(categoriaRepository.findByNombreIgnoreCase("Frutas"))
                .thenReturn(Optional.of(Categoria.builder().nombre("FRUTAS").build()));

        DatosInicialesResultado resultado = servicio.cargarDatosIniciales(Rubro.VERDULERIA);

        List<String> esperadas = Rubro.VERDULERIA.getCategorias().stream().filter(c -> !c.equals("Frutas")).toList();

        assertThat(resultado.existentes()).containsExactly("Frutas");
        assertThat(resultado.creadas()).containsExactlyElementsOf(esperadas);

        ArgumentCaptor<Categoria> creadas = ArgumentCaptor.forClass(Categoria.class);
        verify(categoriaRepository, times(esperadas.size())).save(creadas.capture());
        assertThat(creadas.getAllValues()).extracting(Categoria::getNombre).containsExactlyElementsOf(esperadas);

        assertThat(guardada.getUnidadMedidaPorDefecto()).isEqualTo(UnidadMedida.KILOGRAMO);
    }
}
