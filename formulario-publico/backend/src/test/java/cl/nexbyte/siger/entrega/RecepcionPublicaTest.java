package cl.nexbyte.siger.entrega;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class RecepcionPublicaTest {
    @TempDir Path directory;
    static final SobreComunicacion.Identidad IDENTITY = new SobreComunicacion.Identidad("44444444-4", "Ciudadana", "Prueba");
    @Test void reintentoHistoricoConIdentidadEstructuradaConservaElRegistroOriginal() throws Exception {
        var key=UUID.randomUUID();var mapper=JsonMapper.builder().build();
        try(var store=new RecepcionArchivosDemo(directory,mapper)) {
            var original=store.recibir(key,new SobreComunicacion.Identidad("44444444-4","María José De los ángeles","Del Río González"),"Prueba");
            var structured=new SobreComunicacion.Identidad("44444444-4","María José De los ángeles","Del Río González",java.util.List.of("María José","De los ángeles"),java.util.List.of("Del Río","González"));
            assertThat(store.recibir(key,structured,"Prueba")).isEqualTo(original);
            assertThat(store.pendientes(1).getFirst().identidad().nombresComponentes()).isNull();
        }
    }
    @Test void persisteSobreYConfirmacionYReintentaSinDuplicarInclusoTrasReinicio() throws Exception {
        var key = UUID.randomUUID();
        SobreComunicacion original;
        var mapper = JsonMapper.builder().build();
        try (var store = new RecepcionArchivosDemo(directory, mapper)) {
            original = store.recibir(key, IDENTITY, "Mensaje de prueba");
            assertThat(store.recibir(key, IDENTITY, "Mensaje de prueba")).isEqualTo(original);
            assertThat(store.consultar(key, IDENTITY.run()).estado()).isEqualTo("PENDIENTE_SIGER");
            assertThat(store.pendientes(20)).containsExactly(original);
            assertThatThrownBy(() -> store.recibir(key, IDENTITY, "Otro mensaje")).isInstanceOf(EntregaException.class);
            assertThatThrownBy(() -> store.consultar(key, "11111111-1")).isInstanceOf(EntregaException.class);
        }
        var ack = new SobreComunicacion.Confirmacion(UUID.randomUUID(), Instant.now(), original.sha256());
        try (var store = new RecepcionArchivosDemo(directory, mapper)) {
            assertThat(store.recibir(key, IDENTITY, "Mensaje de prueba")).isEqualTo(original);
            store.confirmar(key, ack); store.confirmar(key, ack);
            assertThat(store.pendientes(20)).isEmpty();
            assertThatThrownBy(() -> store.confirmar(key, new SobreComunicacion.Confirmacion(UUID.randomUUID(), Instant.now(), original.sha256())))
                    .isInstanceOf(EntregaException.class);
        }
        try (var store = new RecepcionArchivosDemo(directory, mapper)) {
            assertThat(store.consultar(key, IDENTITY.run()).confirmacion()).isEqualTo(ack);
            assertThat(store.pendientes(20)).isEmpty();
        }
    }
    @Test void corrupcionNoSeExportaNiSeConfirmaYNoPermiteDosEscritores() throws Exception {
        try (var store = new RecepcionArchivosDemo(directory, JsonMapper.builder().build())) {
            assertThatThrownBy(() -> new RecepcionArchivosDemo(directory, JsonMapper.builder().build())).isInstanceOf(Exception.class);
            var sobre = store.recibir(UUID.randomUUID(), IDENTITY, "Mensaje");
            var path = directory.resolve(sobre.id() + ".json");
            java.nio.file.Files.writeString(path, java.nio.file.Files.readString(path).replace("Mensaje", "Alterado"));
            assertThatThrownBy(() -> store.pendientes(20)).isInstanceOf(EntregaException.class);
            assertThatThrownBy(() -> store.confirmar(sobre.id(), new SobreComunicacion.Confirmacion(UUID.randomUUID(), Instant.now(), sobre.sha256())))
                    .isInstanceOf(EntregaException.class);
        }
    }
}
