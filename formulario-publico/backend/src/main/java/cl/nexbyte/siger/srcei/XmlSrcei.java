package cl.nexbyte.siger.srcei;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import static cl.nexbyte.siger.srcei.ResultadoSrcei.Estado.*;

public final class XmlSrcei {
    public static final String ENTRADA = "entradaSRCel"; // U+006C final, comprobado en DEV Linux.
    public static final int MAX_BYTES = 131072;
    private static final Set<String> OBLIGATORIOS = Set.of("nombres", "primerApellido", "fechaNacimiento", "sexo", "nacionalidad");
    private static final Map<String,String> SEXO = Map.of("F","Femenino","M","Masculino","I","Indeterminado");
    private static final Map<String,String> CIVIL = Map.of("S","Soltero","C","Casado","V","Viudo","D","Divorciado","P","Conviviente civil","J","Separado judicialmente");
    private static final Map<String,String> NACIONALIDAD = Map.of("C","Chilena","E","Extranjera","N","Nacionalizado");
    private XmlSrcei() {}

    public static String solicitud(RunConsulta run) {
        return "<documento><" + ENTRADA + "><run><numero>" + run.numero() + "</numero><dv>" + run.dv()
            + "</dv></run></" + ENTRADA + "></documento>";
    }

    public static ResultadoSrcei respuesta(byte[] xml, RunConsulta solicitado, Instant at, UUID evidence) {
        if (xml.length > MAX_BYTES) return ResultadoSrcei.fallo(RESPUESTA_EXCESIVA, at, evidence);
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
            });
            builder.setEntityResolver((publicId, systemId) -> { throw new SAXException("Entidad no permitida"); });
            Element root = builder.parse(new ByteArrayInputStream(xml)).getDocumentElement();
            checkTree(root, 0, new int[]{0});
            if (!"documento".equals(root.getLocalName())) throw new IllegalArgumentException();
            Element persona = child(root, "datosPersona");
            String glosa = text(root, "glosa");
            if (persona == null) return ResultadoSrcei.fallo(glosa == null ? XML_INVALIDO : NEGATIVA, at, evidence);
            var attrs = new HashMap<String, ResultadoSrcei.Atributo>();
            Element run = child(persona, "run");
            if (run != null) {
                if (!solicitado.numero().equals(text(run, "numero")) || !solicitado.dv().equalsIgnoreCase(Objects.toString(text(run, "dv"), "")))
                    return ResultadoSrcei.fallo(RUN_DISCREPANTE, at, evidence);
                add(attrs, "runNumero", solicitado.numero(), null, at, evidence);
                add(attrs, "runDv", solicitado.dv(), null, at, evidence);
            }
            Element name = child(persona, "nombre");
            if (name != null) {
                // El SRCeI retorna nombres agregados: NO inferir límites por espacios.
                add(attrs, "nombres", text(name, "nombres"), null, at, evidence);
                add(attrs, "primerApellido", text(name, "apellidoPaterno"), null, at, evidence);
                add(attrs, "segundoApellido", text(name, "apellidoMaterno"), null, at, evidence);
            }
            for (String field : List.of("fechaNacimiento", "fechaDefuncion", "fechaDeMuertePresunta")) {
                Element date = child(persona, field);
                if (date != null) {
                    String valid = text(date, "fechaValida");
                    if (valid != null) {
                        try { if (!valid.matches("[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}")) throw new DateTimeParseException("", valid, 0);
                            LocalDate.parse(valid); add(attrs, field, valid, null, at, evidence);
                        } catch (DateTimeParseException ignored) { /* Ausente/truncada no se inventa. */ }
                    }
                    String truncated = text(date, "fechaTruncada");
                    if (truncated != null) add(attrs, field + "Truncada", truncated, "TRUNCADA", at, evidence);
                }
            }
            coded(attrs, persona, "sexo", SEXO, at, evidence);
            coded(attrs, persona, "estadoCivil", CIVIL, at, evidence);
            coded(attrs, persona, "nacionalidad", NACIONALIDAD, at, evidence);
            add(attrs, "profesion", text(persona, "profesion"), null, at, evidence);
            add(attrs, "circunscripcionNacimiento", text(persona, "circunscripcionNacimiento"), null, at, evidence);
            var meaningful = new HashSet<>(attrs.keySet()); meaningful.removeAll(Set.of("runNumero", "runDv"));
            boolean complete = attrs.keySet().containsAll(OBLIGATORIOS)
                && !"Desconocido".equals(attrs.get("sexo").valor()) && !"Desconocido".equals(attrs.get("nacionalidad").valor());
            var state = meaningful.isEmpty() ? VACIO : complete ? COMPLETO : PARCIAL;
            if (state == VACIO && glosa != null && !glosa.toLowerCase(Locale.ROOT).contains("no existe información")) state = NEGATIVA;
            return new ResultadoSrcei(state, attrs, at, evidence);
        } catch (Exception invalid) {
            return ResultadoSrcei.fallo(XML_INVALIDO, at, evidence);
        }
    }
    private static void checkTree(Node node, int depth, int[] count) {
        if (depth > 24 || ++count[0] > 2048) throw new IllegalArgumentException();
        for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling()) checkTree(n, depth + 1, count);
    }
    private static Element child(Element parent, String name) {
        Element result = null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && name.equals(e.getLocalName())) {
                if (result != null) throw new IllegalArgumentException();
                result = e;
            }
        }
        return result;
    }
    private static String text(Element parent, String field) {
        Element e = child(parent, field);
        if (e == null) return null;
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) if (n instanceof Element) throw new IllegalArgumentException();
        String value = e.getTextContent();
        if (value.length() > 500 || value.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException();
        return value.isBlank() || "(NO INFORMADO)".equals(value.strip()) ? null : value;
    }
    private static void add(Map<String, ResultadoSrcei.Atributo> attrs, String key, String value, String code, Instant at, UUID evidence) {
        if (value != null) attrs.put(key, new ResultadoSrcei.Atributo(value, code, "SRCEI", "CONTRASTADO_EN_FUENTE", at, evidence));
    }
    private static void coded(Map<String, ResultadoSrcei.Atributo> attrs, Element parent, String key, Map<String,String> dictionary, Instant at, UUID evidence) {
        String raw = text(parent, key);
        if (raw == null || "U".equals(raw.strip())) return;
        String code = raw.strip();
        add(attrs, key, dictionary.getOrDefault(code, "Desconocido"), code, at, evidence);
    }
}
