package cl.nexbyte.siger.formulario;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Dominio propio: jamás sustituye el algoritmo ni las huellas del contrato histórico. */
public final class CanonFormulario {
    private CanonFormulario(){}
    public static String huella(String dominio,Object value,JsonMapper mapper){
        try {
            var digest=MessageDigest.getInstance("SHA-256");digest.update(("SIGER_FORMULARIO_COMPLETO_1\u0000"+dominio+"\u0000").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(mapper.writeValueAsBytes(orden(mapper.valueToTree(value),mapper))));
        }catch(GeneralSecurityException e){throw new IllegalStateException("Huella no disponible");}
    }
    private static JsonNode orden(JsonNode node,JsonMapper mapper){
        if(node.isObject()) {
            var result=mapper.createObjectNode();var keys=new TreeSet<String>();node.properties().forEach(e->keys.add(e.getKey()));
            keys.forEach(key->result.set(key,orden(node.get(key),mapper)));return result;
        }
        if(node.isArray()){var result=mapper.createArrayNode();for(var entry:node)result.add(orden(entry,mapper));return result;}
        return node;
    }
}
