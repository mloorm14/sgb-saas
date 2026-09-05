package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.entity.BaseConocimiento;
import com.uteq.backend.repository.BaseConocimientoRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool que consulta información sobre políticas de la biblioteca:
 * préstamo, devolución, sanciones, etc.
 * Filtra la base de conocimiento por categorías POLITICAS y MULTAS.
 */
@Component
public class InfoPoliticasTool extends AbstractBaseConocimientoTool {

    public InfoPoliticasTool(BaseConocimientoRepository baseConocimientoRepo) {
        super(baseConocimientoRepo);
    }

    @Override
    public String getName() {
        return "info_politicas";
    }

    @Override
    public String getDescription() {
        return "Consulta información sobre las políticas de la biblioteca: "
                + "préstamo, devolución, renovaciones, sanciones, multas y reglas generales.";
    }

    @Override
    protected List<String> getCategorias() {
        return List.of("POLITICAS", "MULTAS");
    }

    @Override
    protected String getRespuestaKey() {
        return "politicas";
    }

    @Override
    protected ObjectNode mapearEntrada(BaseConocimiento bc) {
        ObjectNode nodo = mapper.createObjectNode();
        nodo.put("categoria", bc.getCategoria());
        nodo.put("pregunta", bc.getPreguntaEjemplo());
        nodo.put("respuesta", bc.getRespuesta());
        return nodo;
    }
}
