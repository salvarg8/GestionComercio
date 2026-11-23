package org.gestionComercio.arca;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lectura de las respuestas XML de ARCA por nombre local de elemento (sin
 * importar el prefijo de namespace) y escape de valores para armar pedidos.
 */
final class Xml {

    private Xml() {
    }

    /** Parsea sin resolver DTD ni entidades externas. */
    static Document parsear(String xml) {

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new ArcaException("ARCA devolvió una respuesta que no es XML válido.", e);
        }
    }

    /** Primer elemento con ese nombre local dentro de {@code raiz}. */
    static Optional<Element> elemento(Node raiz, String nombre) {

        NodeList nodos = raiz instanceof Document d
                ? d.getElementsByTagNameNS("*", nombre)
                : ((Element) raiz).getElementsByTagNameNS("*", nombre);

        return nodos.getLength() == 0 ? Optional.empty() : Optional.of((Element) nodos.item(0));
    }

    /** Todos los elementos con ese nombre local dentro de {@code raiz}. */
    static List<Element> elementos(Node raiz, String nombre) {

        NodeList nodos = raiz instanceof Document d
                ? d.getElementsByTagNameNS("*", nombre)
                : ((Element) raiz).getElementsByTagNameNS("*", nombre);

        List<Element> lista = new ArrayList<>(nodos.getLength());

        for (int i = 0; i < nodos.getLength(); i++) {
            lista.add((Element) nodos.item(i));
        }

        return lista;
    }

    /** Texto recortado del primer elemento con ese nombre; vacío si no está o no tiene texto. */
    static Optional<String> texto(Node raiz, String nombre) {
        return elemento(raiz, nombre)
                .map(Node::getTextContent)
                .map(String::trim)
                .filter(t -> !t.isEmpty());
    }

    static String textoObligatorio(Node raiz, String nombre) {
        return texto(raiz, nombre).orElseThrow(() ->
                new ArcaException("La respuesta de ARCA no tiene el dato " + nombre + "."));
    }

    static String escapar(String valor) {

        if (valor == null) {
            return "";
        }

        return valor.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

}
