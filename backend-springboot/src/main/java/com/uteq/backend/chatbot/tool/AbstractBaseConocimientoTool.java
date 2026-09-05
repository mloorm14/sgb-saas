package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.entity.BaseConocimiento;
import com.uteq.backend.repository.BaseConocimientoRepository;

import java.util.List;

/**
 * Template Method para tools de solo-lectura sobre la base de
 * conocimiento (sin parámetros de entrada). Cada subclase aporta
 * las categorías a filtrar, la clave de respuesta y el mapeo
 * de cada entrada.
 */
public abstract class AbstractBaseConocimientoTool extends AbstractChatbotTool {

    protected final BaseConocimientoRepository baseConocimientoRepo;

    protected AbstractBaseConocimientoTool(BaseConocimientoRepository baseConocimientoRepo) {
        this.baseConocimientoRepo = baseConocimientoRepo;
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", mapper.createObjectNode());
        return schema;
    }

    /** Categorías de BaseConocimiento a incluir (ej. HORARIOS). */
    protected abstract List<String> getCategorias();

    /** Clave del array en la respuesta (ej. "horarios"). */
    protected abstract String getRespuestaKey();

    /** Mapea una entrada de la base a su nodo JSON. */
    protected abstract ObjectNode mapearEntrada(BaseConocimiento bc);

    @Override
    public JsonNode execute(JsonNode args) {
        List<String> categorias = getCategorias().stream()
                .map(String::toUpperCase)
                .toList();
        List<BaseConocimiento> entradas = baseConocimientoRepo.findByActivoTrue().stream()
                .filter(bc -> bc.getCategoria() != null && categorias.contains(bc.getCategoria().toUpperCase()))
                .toList();

        ArrayNode array = mapper.createArrayNode();
        for (BaseConocimiento bc : entradas) {
            array.add(mapearEntrada(bc));
        }

        ObjectNode respuesta = mapper.createObjectNode();
        respuesta.set(getRespuestaKey(), array);
        respuesta.put("total", entradas.size());
        return respuesta;
    }
}
