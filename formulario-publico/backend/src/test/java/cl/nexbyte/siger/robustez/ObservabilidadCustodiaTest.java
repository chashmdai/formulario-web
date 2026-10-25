package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.custodia.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ObservabilidadCustodiaTest {
    @TempDir Path temp;ReceptorTransitorioTest r;
    @BeforeEach void setup()throws Exception{r=new ReceptorTransitorioTest();r.temp=temp;r.setup();r.app=r.service(true).cuotaMultimedia(6L*1024*1024*1024);}
    @AfterEach void close()throws Exception{r.close();}
    UUID full(String state){
        String id=UUID.randomUUID().toString(),journey=UUID.randomUUID().toString();
        r.t.f.db.sql.update("INSERT INTO citizen_journey(id,owner_session,kind,notice_version,accepted_channel,state,query_count,created_ms,expires_ms) VALUES(?,?,'NNA','TEST',FALSE,'RECIBIDO',0,?,?)",journey,r.t.f.session,r.t.now.get(),r.t.now.get()+1000);
        r.t.f.db.sql.update("INSERT INTO citizen_submission(id,journey_id,owner_session,request_hash,body,state,created_ms,next_attempt_ms,document_version) VALUES(?,?,?,?,'NO_DEBE_SALIR_RELATO_CONTACTO',?,?,0,'1.0')",id,journey,r.t.f.session,"0".repeat(64),state,r.t.now.get()-400000);return UUID.fromString(id);
    }
    UUID historical(String state){var id=UUID.randomUUID();r.t.f.db.sql.update("INSERT INTO public_delivery(id,owner_run,request_hash,body,state,created_ms,next_attempt_ms,attempts,lease_until_ms) VALUES(?,'12345678-5',?,'NO_DEBE_SALIR_HISTORICO',?,?,0,0,0)",id.toString(),"0".repeat(64),state,r.t.now.get()-600000);return id;}
    void phase(UUID id,String phase){r.t.f.db.sql.update("INSERT INTO custody_lifecycle(submission_id,observed_ms,due_ms,ack_sha256,phase) VALUES(?,?,?,?,?)",id.toString(),r.t.now.get()-Duration.ofDays(4).toMillis(),r.t.now.get()-1000,"0".repeat(64),phase);}
    void guarantee(UUID id,String queue,long expires){r.t.f.db.sql.update("INSERT INTO custody_guarantee(queue_kind,operation_id,evidence,checked_ms,expires_ms) VALUES(?,?,'NO_DEBE_SALIR_GARANTIA',?,?)",queue,id.toString(),r.t.now.get(),expires);}
    @SuppressWarnings("unchecked") static Map<String,Object> map(Map<String,Object> row,String key){return (Map<String,Object>)row.get(key);}
    @Test void agregaEstadosColasGarantiasRetencionHoldsRecuperacionYCapacidadSinDatosPersonales()throws Exception{
        r.receive(false);r.proofs.aceptar(r.proof(r.t.communication,"COMPLETO"));r.app.ejecutar();r.app.conservar("COMPLETO",r.t.communication,true);
        full("PENDIENTE");full("PENDIENTE");full("RESERVADA");full("CUARENTENA");full("NO_DEBE_SALIR_ESTADO");full("CONFIRMADA");
        UUID expired=full("CONFIRMADA");phase(expired,"OBSERVADA");guarantee(expired,"COMPLETO",r.t.now.get()-1);
        phase(full("CONFIRMADA"),"RETIRANDO");phase(full("CONFIRMADA"),"LIBERADA");
        UUID minimized=full("CONFIRMADA");phase(minimized,"MINIMIZADA");r.t.f.db.sql.update("UPDATE citizen_submission SET body=NULL,content_retired=TRUE WHERE id=?",minimized.toString());
        historical("PENDIENTE");historical("RESERVADA");historical("CUARENTENA");historical("CONFIRMADA");UUID oldExpired=historical("CONFIRMADA");guarantee(oldExpired,"HISTORICO",r.t.now.get()-1);
        UUID oldValid=historical("CONFIRMADA");guarantee(oldValid,"HISTORICO",r.t.now.get()+60000);r.t.f.db.sql.update("UPDATE public_delivery SET retention_hold=TRUE,retire_after_ms=? WHERE id=?",r.t.now.get()+10000,oldValid.toString());
        r.t.f.db.sql.update("UPDATE transient_maintenance SET last_ms=?,last_code='CONSERVADOS_POR_CONTROL' WHERE id=1",r.t.now.get()-120000);
        var result=r.app.estado();var full=map(result,"completo");var old=map(result,"historico");var states=map(full,"porEstado");
        assertEquals(2L,states.get("PENDIENTE"));assertEquals(1L,states.get("RESERVADA"));assertEquals(1L,states.get("CUARENTENA"));assertEquals(6L,states.get("CONFIRMADA"));assertEquals(1L,states.get("OTROS"));assertEquals(400L,full.get("pendienteMasAntiguaEdadSegundos"));
        assertEquals(4L,full.get("confirmadasEsperandoGarantia"));assertEquals(Map.of("vigentesPorVencimiento",1L,"ausentes",3L,"vencidas",1L),map(full,"garantias"));assertEquals(1L,full.get("bajoHold"));assertEquals(1L,full.get("esperandoRetencionTecnica"));assertEquals(1L,full.get("sinObservacionRetencion"));
        assertEquals(Map.of("iniciadasAcumuladas",3L,"retirando",1L,"bytesLiberados",2L,"pendientesRecuperacion",2L),map(full,"retiradas"));assertEquals(1L,full.get("minimizadas"));
        assertEquals(600L,old.get("pendienteMasAntiguaEdadSegundos"));assertEquals(2L,old.get("confirmadasEsperandoGarantia"));assertEquals(1L,old.get("bajoHold"));assertEquals(1L,old.get("esperandoRetencionTecnica"));assertEquals(0L,map(old,"retiradas").get("pendientesRecuperacion"));
        assertEquals(120L,map(result,"mantenimiento").get("edadSegundos"));assertEquals("CONSERVADOS_POR_CONTROL",map(result,"mantenimiento").get("resultado"));
        var capacity=map(result,"capacidadMultimedia");assertEquals(r.t.reserved(),capacity.get("reservadosBytes"));assertEquals(6L*1024*1024*1024,capacity.get("cuotaConfiguradaBytes"));assertEquals(r.t.f.free.get(),capacity.get("filesystemDisponibleBytes"));assertEquals(true,capacity.get("contabilidadConcordante"));
        String json=r.t.f.mapper.writeValueAsString(result);assertTrue(json.length()<8192);assertFalse(json.contains("NO_DEBE_SALIR"));assertFalse(json.contains(r.t.communication.toString()));assertFalse(json.contains(r.t.f.journey.toString()));assertFalse(json.contains(r.t.f.session));assertFalse(json.contains("12345678"));assertFalse(json.contains("NOMBRE"));
    }
    @Test void ausenciaDeCuotaYUltimaEjecucionSeRepresentaSinInventarValores()throws Exception{
        var plain=r.service(false);try{var state=plain.estado();assertNull(map(state,"mantenimiento").get("edadSegundos"));assertNull(map(state,"capacidadMultimedia").get("cuotaConfiguradaBytes"));assertNull(map(state,"completo").get("pendienteMasAntiguaEdadSegundos"));assertEquals(false,state.get("eliminacionHabilitada"));}finally{plain.close();}
    }
    @Test void indicesOperativosEstanMigradosEnElMotorDePrueba()throws Exception{
        try(var c=r.t.f.db.sql.getDataSource().getConnection()){
            var names=new HashSet<String>();String table=System.getenv("ROBUST_DB_URL")==null?"CITIZEN_SUBMISSION":"citizen_submission";
            try(var indexes=c.getMetaData().getIndexInfo(c.getCatalog(),null,table,false,false)){while(indexes.next()){var name=indexes.getString("INDEX_NAME");if(name!=null)names.add(name.toLowerCase(Locale.ROOT));}}
            assertTrue(names.contains("ix_submission_retention"));
        }
    }
}