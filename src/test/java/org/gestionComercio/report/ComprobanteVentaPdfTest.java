package org.gestionComercio.report;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.dto.empresa.EmpresaDto;
import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.FormatoTicket;
import org.gestionComercio.enums.TipoComprobante;
import org.gestionComercio.enums.TipoDocumento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ComprobanteVentaPdfTest {

    private static final PDFont HELVETICA = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private static EmpresaDto empresa() {
        return EmpresaDto.builder()
                .razonSocial("Almacén Don José SRL")
                .nombreFantasia("El Almacén")
                .cuit("30712345679")
                .condicionIva(CondicionIVA.RESPONSABLE_INSCRIPTO)
                .direccion("San Martín 123")
                .telefono("351 555-1234")
                .logo("no-existe/logo.png")
                .build();
    }

    private static DetalleVentaDto renglon(String nombre, String cantidad, String precio) {
        BigDecimal c = new BigDecimal(cantidad);
        BigDecimal p = new BigDecimal(precio);
        return DetalleVentaDto.builder()
                .productoCodigo("P-" + nombre.length())
                .productoNombre(nombre)
                .cantidad(c)
                .precioUnitario(p)
                .subtotal(c.multiply(p))
                .build();
    }

    private static VentaDto venta(List<DetalleVentaDto> detalles) {
        return VentaDto.builder()
                .id(7L)
                .fecha(LocalDateTime.of(2026, 11, 20, 10, 30))
                .tipoComprobante(TipoComprobante.REMITO)
                .numeroComprobante(12L)
                .clienteNombre(VentaDto.CONSUMIDOR_FINAL)
                .formaPago(FormaPago.EFECTIVO)
                .subtotal(new BigDecimal("4500.00"))
                .descuento(new BigDecimal("500.00"))
                .total(new BigDecimal("4000.00"))
                .estadoVenta(EstadoVenta.PAGADA)
                .usuarioNombre("ana")
                .usuarioNombreCompleto("Ana Pérez")
                .detalles(new ArrayList<>(detalles))
                .build();
    }

    private static PDDocument leer(byte[] pdf) throws IOException {
        return Loader.loadPDF(pdf);
    }

    private static String texto(byte[] pdf) throws IOException {
        try (PDDocument documento = leer(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }

    @Test
    @DisplayName("Muestra los datos de la empresa, el comprobante, los renglones y los totales")
    void contenido() throws IOException {
        VentaDto venta = venta(List.of(renglon("Gaseosa cola 2,25 L", "3", "1500.00")));

        String texto = texto(ComprobanteVentaPdf.generar(venta, empresa(), null));

        assertThat(texto)
                .contains("Almacén Don José SRL")
                .contains("El Almacén")
                .contains("30-71234567-9")
                .contains("Responsable inscripto")
                .contains("REMITO")
                .contains("N° 00000012")
                .contains("20/11/2026")
                .contains("Consumidor final")
                .contains("Gaseosa cola 2,25 L")
                .contains("TOTAL")
                .contains("4.000,00")
                .contains("Ana Pérez")
                .contains(ComprobanteVentaPdf.LEYENDA_NO_FISCAL)
                .doesNotContain(ComprobanteVentaPdf.ANULADA);
    }

    @Test
    @DisplayName("Con cliente identificado muestra su nombre y documento")
    void conCliente() throws IOException {
        ClienteDto cliente = ClienteDto.builder()
                .nombre("Juan").apellido("Gómez")
                .tipoDocumento(TipoDocumento.CUIT).numeroDocumento("20123456786")
                .condicionIva(CondicionIVA.CONSUMIDOR_FINAL)
                .direccion("Belgrano 45")
                .build();

        String texto = texto(ComprobanteVentaPdf.generar(
                venta(List.of(renglon("Yerba", "1", "4500.00"))), empresa(), cliente));

        assertThat(texto).contains("Gómez, Juan").contains("20-12345678-6").contains("Belgrano 45");
    }

    @Test
    @DisplayName("Una venta anulada sale marcada como ANULADA con el motivo")
    void anulada() throws IOException {
        VentaDto venta = venta(List.of(renglon("Yerba", "1", "4500.00")));
        venta.setEstadoVenta(EstadoVenta.ANULADA);
        venta.setFechaAnulacion(LocalDateTime.of(2026, 11, 20, 11, 0));
        venta.setMotivoAnulacion("Error en la carga");
        venta.setUsuarioAnulacionNombreCompleto("Ana Pérez");

        String texto = texto(ComprobanteVentaPdf.generar(venta, empresa(), null));

        assertThat(texto).contains(ComprobanteVentaPdf.ANULADA).contains("Error en la carga");
    }

    @Test
    @DisplayName("Con muchos renglones continúa en otras páginas, numeradas")
    void variasPaginas() throws IOException {
        List<DetalleVentaDto> detalles = IntStream.rangeClosed(1, 120)
                .mapToObj(i -> renglon("Producto " + i, "1", "10.00"))
                .toList();

        byte[] pdf = ComprobanteVentaPdf.generar(venta(detalles), empresa(), null);

        try (PDDocument documento = leer(pdf)) {
            int paginas = documento.getNumberOfPages();
            assertThat(paginas).isGreaterThan(1);

            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains("Producto 1").contains("Producto 120")
                    .contains("Página " + paginas + " de " + paginas);
        }
    }

    @Test
    @DisplayName("Los caracteres que la fuente no puede escribir no rompen el PDF")
    void caracteresNoSoportados() throws IOException {
        VentaDto venta = venta(List.of(renglon("Té verde 🍵 — “premium”", "1", "4500.00")));

        String texto = texto(ComprobanteVentaPdf.generar(venta, empresa(), null));

        assertThat(texto).contains("Té verde ? — “premium”");
    }

    // ------------------------------------------------------------------
    // Ticket (rollo de impresora térmica)
    // ------------------------------------------------------------------

    private static VentaDto ticket(int renglones) {
        VentaDto venta = venta(IntStream.rangeClosed(1, renglones)
                .mapToObj(i -> renglon("Producto " + i, "2", "1500.00"))
                .toList());
        venta.setTipoComprobante(TipoComprobante.TICKET);
        return venta;
    }

    private static PDRectangle pagina(byte[] pdf) throws IOException {
        try (PDDocument documento = leer(pdf)) {
            assertThat(documento.getNumberOfPages()).isEqualTo(1);
            return documento.getPage(0).getMediaBox();
        }
    }

    private static OpcionesComprobante formato(FormatoTicket formato) {
        return new OpcionesComprobante(formato, true, null);
    }

    @Test
    @DisplayName("El ticket sale en una sola página con el ancho del rollo configurado (80 mm por defecto)")
    void ticketAnchoDelRollo() throws IOException {
        EmpresaDto empresa = empresa();

        assertThat(pagina(ComprobanteVentaPdf.generar(ticket(3), empresa, null)).getWidth())
                .isCloseTo(80 * 72f / 25.4f, within(0.01f));

        assertThat(pagina(ComprobanteVentaPdf.generar(ticket(3), empresa, null,
                formato(FormatoTicket.TERMICO_58))).getWidth())
                .isCloseTo(58 * 72f / 25.4f, within(0.01f));
    }

    @Test
    @DisplayName("Con el formato A4, el ticket sale en una hoja A4")
    void ticketEnA4() throws IOException {
        byte[] pdf = ComprobanteVentaPdf.generar(ticket(3), empresa(), null, formato(FormatoTicket.A4));

        PDRectangle hoja = pagina(pdf);
        assertThat(hoja.getWidth()).isCloseTo(PDRectangle.A4.getWidth(), within(0.01f));
        assertThat(hoja.getHeight()).isCloseTo(PDRectangle.A4.getHeight(), within(0.01f));
        assertThat(texto(pdf)).contains("TICKET").contains("Producto 3");
    }

    @Test
    @DisplayName("El texto de pie sale en el ticket térmico y en A4, renglón por renglón")
    void textoDePie() throws IOException {
        String pie = "Lunes a sábados de 9 a 20 h\nCambios dentro de los 30 días";

        for (FormatoTicket formato : FormatoTicket.values()) {
            String texto = texto(ComprobanteVentaPdf.generar(ticket(2), empresa(), null,
                    new OpcionesComprobante(formato, true, pie)));

            assertThat(texto).as(formato.name())
                    .contains("Lunes a sábados de 9 a 20 h")
                    .contains("Cambios dentro de los 30 días");
        }

        // Las facturas y remitos (A4) también lo llevan.
        assertThat(texto(ComprobanteVentaPdf.generar(venta(List.of(renglon("Yerba", "1", "100.00"))), empresa(), null,
                new OpcionesComprobante(FormatoTicket.TERMICO_80, true, pie))))
                .contains("Cambios dentro de los 30 días");
    }

    @Test
    @DisplayName("El largo del ticket crece con los renglones, sin pasar a otra página")
    void ticketLargoSegunRenglones() throws IOException {
        float corto = pagina(ComprobanteVentaPdf.generar(ticket(2), empresa(), null)).getHeight();
        float largo = pagina(ComprobanteVentaPdf.generar(ticket(80), empresa(), null)).getHeight();

        assertThat(largo).isGreaterThan(corto * 5);
    }

    @Test
    @DisplayName("El ticket muestra la empresa, el número, los renglones y el total")
    void ticketContenido() throws IOException {
        String texto = texto(ComprobanteVentaPdf.generar(ticket(2), empresa(), null));

        assertThat(texto)
                .contains("Almacén Don José SRL")
                .contains("30-71234567-9")
                .contains("TICKET")
                .contains("N° 00000012")
                .contains("Producto 2")
                .contains("2 x $")
                .contains("TOTAL")
                .contains("4.000,00")
                .contains(ComprobanteVentaPdf.LEYENDA_NO_FISCAL)
                .doesNotContain(ComprobanteVentaPdf.ANULADA);
    }

    @Test
    @DisplayName("Un ticket anulado sale marcado como ANULADA con el motivo, también en 58 mm")
    void ticketAnulado() throws IOException {
        VentaDto venta = ticket(2);
        venta.setEstadoVenta(EstadoVenta.ANULADA);
        venta.setFechaAnulacion(LocalDateTime.of(2026, 11, 20, 11, 0));
        venta.setMotivoAnulacion("Error en la carga");

        String texto = texto(ComprobanteVentaPdf.generar(venta, empresa(), null, formato(FormatoTicket.TERMICO_58)));

        assertThat(texto).contains(ComprobanteVentaPdf.ANULADA).contains("Error en la carga");
    }

    @Test
    @DisplayName("limpiar() reemplaza lo que no se puede escribir y convierte saltos de línea en espacios")
    void limpiar() {
        assertThat(ComprobanteVentaPdf.limpiar(HELVETICA, "Año\nñandú\t✓")).isEqualTo("Año ñandú ?");
        assertThat(ComprobanteVentaPdf.limpiar(HELVETICA, null)).isEmpty();
    }

    @Test
    @DisplayName("recortar() agrega puntos suspensivos sólo si el texto no entra")
    void recortar() throws IOException {
        assertThat(ComprobanteVentaPdf.recortar(HELVETICA, 9, "Corto", 100)).isEqualTo("Corto");

        String largo = "Un nombre de producto larguísimo que no entra en la columna";
        String recortado = ComprobanteVentaPdf.recortar(HELVETICA, 9, largo, 100);

        assertThat(recortado).endsWith("…").hasSizeLessThan(largo.length());
        assertThat(ComprobanteVentaPdf.ancho(HELVETICA, 9, recortado)).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("partir() corta por palabras sin superar el ancho")
    void partir() throws IOException {
        List<String> lineas = ComprobanteVentaPdf.partir(HELVETICA, 9,
                "Motivo: el cliente devolvió la mercadería porque estaba vencida y pidió el reintegro", 150);

        assertThat(lineas).hasSizeGreaterThan(1);
        for (String linea : lineas) {
            assertThat(ComprobanteVentaPdf.ancho(HELVETICA, 9, linea)).isLessThanOrEqualTo(150);
        }
    }
}
