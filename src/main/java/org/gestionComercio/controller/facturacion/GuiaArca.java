package org.gestionComercio.controller.facturacion;

import org.gestionComercio.enums.AmbienteArca;
import org.gestionComercio.service.ConfiguracionArcaService;

/**
 * Textos de la guía para configurar la facturación electrónica en el sitio
 * de ARCA, según el ambiente. Escritos para alguien sin conocimientos
 * técnicos: qué servicio abrir, qué elegir y qué anotar.
 */
final class GuiaArca {

    /** Ingreso con clave fiscal al sitio de ARCA. */
    static final String URL_CLAVE_FISCAL = "https://auth.afip.gob.ar/contribuyente_/login.xhtml";

    private static final String ALIAS = ConfiguracionArcaService.ALIAS_COMPUTADOR;

    private GuiaArca() {
    }

    static String clave() {
        return "El certificado se guarda cifrado en la base de datos con una clave que no queda en la base: "
                + "se guarda en cada PC. Así, quien tenga una copia de la base no puede facturar a nombre del "
                + "comercio. Es la misma clave en todas las PC: se elige en la primera y se escribe una vez en "
                + "cada PC que vaya a facturar.";
    }

    static String clavePrimeraVez() {
        return "Elija una clave de al menos 8 caracteres y anótela en un lugar seguro: la va a necesitar en cada "
                + "PC nueva. Si se pierde, hay que generar un certificado nuevo en ARCA.";
    }

    static String claveOtraPc() {
        return "Ya hay un certificado guardado: escriba la misma clave que se eligió al configurar la facturación "
                + "en la primera PC.";
    }

    static String ambiente() {
        return "Homologación es el ambiente de pruebas de ARCA: las facturas que se emiten ahí no tienen validez "
                + "fiscal. Conviene configurar y probar todo primero en homologación y después pasar a producción. "
                + "Cada ambiente usa su propio certificado: al cambiar de ambiente hay que repetir los pasos del "
                + "certificado (5 a 8).";
    }

    static String puntoDeVenta(AmbienteArca ambiente) {

        if (ambiente == AmbienteArca.HOMOLOGACION) {
            return "En homologación no hace falta crear el punto de venta en ARCA: indique cualquier número, por "
                    + "ejemplo 1, y guárdelo.";
        }

        return "La factura electrónica necesita un punto de venta propio, distinto del que use para facturar en "
                + "\"Comprobantes en línea\" o con controlador fiscal.\n"
                + "1. Ingrese a ARCA con el CUIT y la clave fiscal del comercio.\n"
                + "2. Abra el servicio \"Administración de puntos de venta y domicilios\" y elija la empresa.\n"
                + "3. Entre en \"A/B/M de puntos de venta\" y presione \"Agregar\".\n"
                + "4. Complete un número que no use (por ejemplo, 3) y elija el sistema \"RECE para aplicativo y "
                + "web services\" (responsable inscripto o exento) o \"Factura Electrónica - Monotributo - Web "
                + "Services\" (monotributista). Elija el domicilio y acepte.\n"
                + "5. Escriba ese número aquí abajo y guárdelo.";
    }

    static String generarPedido() {
        return "El sistema genera la clave privada del comercio (queda guardada cifrada, nunca sale de aquí) y un "
                + "\"pedido de certificado\": un archivo .csr que hay que subir a ARCA en el paso siguiente. Al "
                + "generarlo se guarda el archivo y su contenido queda copiado (para pegarlo en ARCA).";
    }

    static String obtenerCertificado(AmbienteArca ambiente) {

        if (ambiente == AmbienteArca.HOMOLOGACION) {
            return "1. Ingrese a ARCA con el CUIT y la clave fiscal del comercio.\n"
                    + "2. Abra el servicio \"WSASS - Autogestión Certificados Homologación\". Si no aparece, "
                    + "agréguelo desde \"Administrador de Relaciones de Clave Fiscal\" > \"Adherir servicio\" > "
                    + "ARCA > Servicios interactivos.\n"
                    + "3. Elija \"Nuevo certificado\". En \"Nombre simbólico del DN\" escriba " + ALIAS + " y en "
                    + "\"Solicitud de certificado en formato PKCS#10\" pegue el pedido (ya está copiado; si no, "
                    + "abra el archivo .csr con el Bloc de notas y copie todo su contenido).\n"
                    + "4. Presione \"Crear DN y obtener certificado\". Copie el certificado que aparece (desde "
                    + "-----BEGIN CERTIFICATE----- hasta -----END CERTIFICATE-----), péguelo en el Bloc de notas "
                    + "y guárdelo con el nombre certificado.crt.";
        }

        return "1. Ingrese a ARCA con el CUIT y la clave fiscal del comercio.\n"
                + "2. Abra el servicio \"Administración de Certificados Digitales\". Si no aparece, agréguelo desde "
                + "\"Administrador de Relaciones de Clave Fiscal\" > \"Adherir servicio\" > ARCA > Servicios "
                + "interactivos.\n"
                + "3. Presione \"Agregar alias\". En \"Alias\" escriba " + ALIAS + ", elija el archivo .csr "
                + "generado en el paso anterior y presione \"Agregar alias\".\n"
                + "4. Presione \"Ver\" en el alias " + ALIAS + " y descargue el certificado (archivo .crt).";
    }

    static String importarCertificado() {
        return "Elija el archivo .crt que descargó de ARCA. Tiene que corresponder al último pedido generado en "
                + "este sistema. Si ya tiene un certificado con su clave privada en un archivo .p12 o .pfx "
                + "(generado con otro programa), puede importarlo en su lugar.";
    }

    static String autorizarCertificado(AmbienteArca ambiente) {

        if (ambiente == AmbienteArca.HOMOLOGACION) {
            return "En el servicio \"WSASS - Autogestión Certificados Homologación\" elija \"Crear autorización a "
                    + "servicio\". Elija el nombre simbólico " + ALIAS + ", el servicio \"wsfe - Facturación "
                    + "Electrónica\" y presione \"Crear autorización de acceso\".";
        }

        return "1. En ARCA abra el servicio \"Administrador de Relaciones de Clave Fiscal\".\n"
                + "2. Presione \"Nueva relación\" y luego \"Buscar\": elija ARCA > WebServices > \"Facturación "
                + "Electrónica\".\n"
                + "3. En \"Representante\" presione \"Buscar\" y elija el computador fiscal " + ALIAS + ".\n"
                + "4. Presione \"Confirmar\".";
    }

    static String probarConexion() {
        return "Verifica que esta PC llegue a ARCA, que ARCA acepte el certificado y que el punto de venta esté "
                + "habilitado para factura electrónica. No emite ningún comprobante.";
    }

    static String habilitar() {
        return "Con la facturación habilitada, el punto de venta ofrece la factura que corresponde a cada cliente "
                + "(A, B o C) y pide el CAE a ARCA al confirmar la venta. Al anular una venta facturada se emite "
                + "la nota de crédito.";
    }

}
