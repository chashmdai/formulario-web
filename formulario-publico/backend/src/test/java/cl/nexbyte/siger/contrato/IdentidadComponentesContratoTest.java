package cl.nexbyte.siger.contrato;
import cl.nexbyte.siger.entrega.SobreComunicacion;
import java.util.*;import org.junit.jupiter.api.Test;import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
class IdentidadComponentesContratoTest {
 final JsonMapper mapper=JsonMapper.builder().build();
 @Test void vectorEstructuradoPreservaValoresYHuellaInteroperable(){
  var body=mapper.readValue(getClass().getResourceAsStream("/contratos/comunicacion-identidad-v1.1.json"),SobreComunicacion.class);
  assertThat(body.integra()).isTrue();assertThat(body.sha256()).isEqualTo("6419a1f86fd4ed25238425ae090e883578673219aa8c0580b49ae1417d552bd0");
  assertThat(body.identidad().nombresComponentes()).containsExactly("  María José  ","De los ángeles","Tercero","Cuarto");
  assertThat(mapper.readValue(mapper.writeValueAsBytes(body),SobreComunicacion.class)).isEqualTo(body);
 }
 @Test void historicoNoAdquiereComponentesNiCambiaSuHuella(){
  var old=mapper.readValue(getClass().getResourceAsStream("/comunicacion-v1.json"),SobreComunicacion.class);
  assertThat(old.version()).isEqualTo("1.0");assertThat(old.integra()).isTrue();
  assertThat(old.identidad().nombresComponentes()).isNull();assertThat(old.identidad().apellidosComponentes()).isNull();
  assertThat(mapper.writeValueAsString(old)).doesNotContain("nombresComponentes","apellidosComponentes");
  assertThat(mapper.readValue(mapper.writeValueAsBytes(old),SobreComunicacion.class)).isEqualTo(old);
 }
 @Test void redistribuirLimitesConTextoIgualInvalidaHuellaYNoPermiteDegradacion(){
  var a=new SobreComunicacion.Identidad("44444444-4","María José De los ángeles","Del Río González",List.of("María José","De los ángeles"),List.of("Del Río","González"));
  var b=new SobreComunicacion.Identidad(a.run(),a.nombres(),a.apellidos(),List.of("María","José De los ángeles"),a.apellidosComponentes());
  var original=SobreComunicacion.crear(UUID.randomUUID(),java.time.Instant.now(),a,"Prueba sintética","test");
  assertThat(new SobreComunicacion(original.version(),original.id(),original.recibidaEn(),b,original.mensaje(),original.avisoPrivacidadVersion(),original.sha256()).integra()).isFalse();
  assertThatThrownBy(()->new SobreComunicacion("1.0",original.id(),original.recibidaEn(),a,original.mensaje(),original.avisoPrivacidadVersion(),original.sha256())).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new SobreComunicacion.Identidad(a.run(),"Texto adulterado",a.apellidos(),a.nombresComponentes(),a.apellidosComponentes())).isInstanceOf(IllegalArgumentException.class);
 }
}
