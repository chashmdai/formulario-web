package cl.nexbyte.siger.srcei;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static cl.nexbyte.siger.srcei.ResultadoSrcei.Estado.*;

class XmlSrceiTest {
    static final RunConsulta RUN = new RunConsulta("1", "9"); // Sintético; nunca consultar al proveedor.
    static final Instant NOW = Instant.parse("2026-10-23T20:00:00Z");
    static final String PERSONA = "<run><numero>1</numero><dv>9</dv></run><nombre><nombres>Nombre Compuesto</nombres>"
        + "<apellidoPaterno>Apellido Sintético</apellidoPaterno><apellidoMaterno>Prueba</apellidoMaterno></nombre>"
        + "<fechaNacimiento><fechaValida>2000-02-29</fechaValida></fechaNacimiento><sexo>I</sexo><estadoCivil>P</estadoCivil><nacionalidad>N</nacionalidad>"
        + "<profesion>(NO INFORMADO)</profesion><fechaDefuncion><fechaTruncada>0000-00-00</fechaTruncada></fechaDefuncion>";
    static ResultadoSrcei parse(String xml) { return XmlSrcei.respuesta(xml.getBytes(StandardCharsets.UTF_8), RUN, NOW, UUID.randomUUID()); }
    static String response(String fields) { return "<documento xmlns='http://valida.aem.gob.cl'><datosPersona>" + fields + "</datosPersona></documento>"; }

    @Test void solicitudUsaEleMinusculaConfirmadaSinCambiarlaPorIMayuscula() {
        String xml = XmlSrcei.solicitud(RUN);
        assertThat(XmlSrcei.ENTRADA.codePointAt(XmlSrcei.ENTRADA.length() - 1)).isEqualTo(0x6c);
        assertThat(xml).isEqualTo("<documento><entradaSRCel><run><numero>1</numero><dv>9</dv></run></entradaSRCel></documento>")
            .doesNotContain("entradaSRCeI");
        assertThatThrownBy(() -> new RunConsulta("1</numero>", "9")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void completoConProcedenciaNoAutenticaNiInfiereNombresOResidencia() {
        var result = parse(response(PERSONA));
        assertThat(result.estado()).isEqualTo(COMPLETO);
        assertThat(result.atributos().get("nombres").valor()).isEqualTo("Nombre Compuesto");
        assertThat(result.atributos().get("sexo").valor()).isEqualTo("Indeterminado");
        assertThat(result.atributos().get("estadoCivil").valor()).isEqualTo("Conviviente civil");
        assertThat(result.atributos().get("nacionalidad").valor()).isEqualTo("Nacionalizado");
        assertThat(result.atributos()).doesNotContainKeys("region", "comuna", "primerNombre", "profesion", "fechaDefuncion");
        assertThat(result.atributos().values()).allSatisfy(a -> {
            assertThat(a.fuente()).isEqualTo("SRCEI"); assertThat(a.nivel()).isEqualTo("CONTRASTADO_EN_FUENTE");
            assertThat(a.observadoEn()).isEqualTo(NOW); assertThat(a.evidencia()).isEqualTo(result.evidencia());
        });
        assertThat(result.toString()).doesNotContain("Nombre Compuesto", "Apellido");
        assertThat(result.atributos().get("nombres").toString()).doesNotContain("Nombre");
    }
    @Test void parcialVacioYGlosaNegativaSonResultadosDistintos() {
        assertThat(parse(response("<nombre><nombres>Sintético</nombres></nombre>")).estado()).isEqualTo(PARCIAL);
        assertThat(parse(response("")).estado()).isEqualTo(VACIO);
        assertThat(parse("<documento><glosa>No existe información disponible.</glosa><datosPersona/></documento>").estado()).isEqualTo(VACIO);
        assertThat(parse("<documento><glosa>Consulta no autorizada</glosa><datosPersona/></documento>").estado()).isEqualTo(NEGATIVA);
        assertThat(parse("<documento><glosa>Servicio rechazado</glosa></documento>").estado()).isEqualTo(NEGATIVA);
    }
    @Test void fechasInvalidasYTruncadasNoSeConviertenEnFechasInventadas() {
        var result = parse(response(PERSONA.replace("2000-02-29", "2001-02-29")));
        assertThat(result.estado()).isEqualTo(PARCIAL); assertThat(result.atributos()).doesNotContainKey("fechaNacimiento");
        result = parse(response(PERSONA.replace("2000-02-29", "0000-00-00")));
        assertThat(result.atributos()).doesNotContainKey("fechaNacimiento");
        assertThat(result.atributos().get("fechaDefuncionTruncada").codigo()).isEqualTo("TRUNCADA");
    }
    @Test void codigosDesconocidosSeConservanComoDesconocidosYUNoInforma() {
        var result = parse(response(PERSONA.replace("<sexo>I", "<sexo>Z")));
        assertThat(result.estado()).isEqualTo(PARCIAL);
        assertThat(result.atributos().get("sexo").codigo()).isEqualTo("Z");
        assertThat(result.atributos().get("sexo").valor()).isEqualTo("Desconocido");
        result = parse(response(PERSONA.replace("<sexo>I", "<sexo>U")));
        assertThat(result.estado()).isEqualTo(PARCIAL); assertThat(result.atributos()).doesNotContainKey("sexo");
    }
    @Test void runDiferenteNoSeMezclaConElSolicitado() {
        var result = parse(response(PERSONA.replace("<numero>1", "<numero>2")));
        assertThat(result.estado()).isEqualTo(RUN_DISCREPANTE); assertThat(result.atributos()).isEmpty();
    }
    @Test void rechazaXxeDtdBillionLaughsDuplicadosProfundidadYXmlInesperado() {
        for (String xml : java.util.List.of(
            "<!DOCTYPE documento [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><documento><datosPersona>&x;</datosPersona></documento>",
            "<!DOCTYPE documento [<!ENTITY a 'aaaa'><!ENTITY b '&a;&a;'>]><documento>&b;</documento>",
            "<documento><datosPersona/><datosPersona/></documento>",
            response("<sexo>M</sexo><sexo>F</sexo>"), "<documento><datosPersona>", "<html/>",
            "<documento>" + "<x>".repeat(30) + "</x>".repeat(30) + "</documento>")) {
            var result = parse(xml); assertThat(result.estado()).isEqualTo(XML_INVALIDO); assertThat(result.atributos()).isEmpty();
        }
        assertThat(parse("x".repeat(XmlSrcei.MAX_BYTES + 1)).estado()).isEqualTo(RESPUESTA_EXCESIVA);
    }
}
