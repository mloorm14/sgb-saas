package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.entity.KnowledgeBase;
import com.uteq.backend.repository.BaseKnowledgeRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool que consulta los horarios de apertura de la biblioteca.
 * Filtra la base de conocimiento por categoría HORARIOS.
 */
@Component
public class QuerySchedulesTool extends AbstractKnowledgeBaseTool {

    public QuerySchedulesTool(BaseKnowledgeRepository baseKnowledgeRepo) {
        super(baseKnowledgeRepo);
    }

    @Override
    /**
     * Retrieves name.
     *
     * @return resulting text payload
     */
    public String getName() {
        return "consultar_horarios";
    }

    @Override
    /**
     * Retrieves scription.
     *
     * @return resulting text payload
     */
    public String getDescription() {
        return "Consulta los horarios de apertura de la biblioteca. "
                + "Incluye horarios de lunes a viernes, sábados y días especiales.";
    }

    @Override
    protected List<String> getCategories() {
        return List.of("HORARIOS");
    }

    @Override
    protected String getResponseKey() {
        return "horarios";
    }

    @Override
    protected ObjectNode mapearInput(KnowledgeBase bc) {
        ObjectNode node = mapper.createObjectNode();
        node.put("pregunta", bc.getQuestionExample());
        node.put("respuesta", bc.getResponse());
        return node;
    }
}
