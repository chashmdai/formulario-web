package cl.nexbyte.siger.formulario;

import java.io.IOException;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** Catálogo de la referencia visual congelada; no infiere domicilio desde SRCeI. */
public final class TerritorioFormulario {
    private final Map<String,Set<String>> comunas;
    public TerritorioFormulario(JsonMapper mapper) throws IOException {
        try(var stream=TerritorioFormulario.class.getResourceAsStream("/catalogos/territorio.json")) {
            if(stream==null)throw new IllegalStateException("Falta catálogo territorial");
            var root=mapper.readTree(stream);var map=new HashMap<String,Set<String>>();
            for(var region:root.path("comunas").properties()) {
                var names=new HashSet<String>();for(var name:region.getValue())names.add(name.asString());
                map.put(region.getKey(),Set.copyOf(names));
            }
            if(map.size()!=16)throw new IllegalStateException("Catálogo territorial incompleto");
            comunas=Map.copyOf(map);
        }
    }
    public boolean region(String id){return id!=null&&comunas.containsKey(id);}
    public boolean comuna(String region,String nombre){return nombre==null||nombre.isBlank()||comunas.getOrDefault(region,Set.of()).contains(nombre);}
}
