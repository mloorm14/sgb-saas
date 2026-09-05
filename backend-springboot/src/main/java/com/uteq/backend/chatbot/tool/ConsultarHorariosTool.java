package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.entity.BaseConocimiento;
import com.uteq.backend.repository.BaseConocimientoRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool que consulta los horarios de apertura de la biblioteca.
 * Filtra la base de conocimiento por categoría HORARIOS.
 */
@Component
public class ConsultarHorariosTool extends AbstractBaseConocimientoTool {

    public ConsultarHorariosTool(BaseConocimientoRepository baseConocimientoRepo) {
        super(baseConocimientoRepo);
    }

    @Override
    public String getName() {
        return "consultar_horarios";
    }

    @Override
    public String getDescription() {
        return "Consulta los horarios de apertura de la biblioteca. "
                + "Incluye horarios de lunes a viernes, sábados y días especiales.";
    }

    @Override
    protected List<String> getCategorias() {
        return List.of("HORARIOS");
    }

    @Override
    protected String getRespuestaKey() {
        return "horarios";
    }

    @Override
    protected ObjectNode mapearEntrada(BaseConocimiento bc) {
        ObjectNode nodo = mapper.createObjectNode();
        nodo.put("pregunta", bc.getPreguntaEjemplo());
        nodo.put("respuesta", bc.getRespuesta());
        return nodo;
    }
}
