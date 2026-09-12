package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.entity.KnowledgeBase;
import com.uteq.backend.repository.BaseKnowledgeRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool que consulta información sobre políticas de la biblioteca:
 * préstamo, devolución, sanciones, etc.
 * Filtra la base de conocimiento por categorías POLITICAS y MULTAS.
 */
@Component
public class InfoPoliciesTool extends AbstractKnowledgeBaseTool {

    public InfoPoliciesTool(BaseKnowledgeRepository baseKnowledgeRepo) {
        super(baseKnowledgeRepo);
    }

    @Override
    /**
     * Retrieves name.
     *
     * @return resulting text payload
     */
    public String getName() {
        return "info_politicas";
    }

    @Override
    /**
     * Retrieves scription.
     *
     * @return resulting text payload
     */
    public String getDescription() {
        return "Consulta información sobre las políticas de la biblioteca: "
                + "préstamo, devolución, renovaciones, sanciones, multas y reglas generales.";
    }

    @Override
    protected List<String> getCategories() {
        return List.of("POLITICAS", "MULTAS");
    }

    @Override
    protected String getResponseKey() {
        return "politicas";
    }

    @Override
    protected ObjectNode mapearInput(KnowledgeBase bc) {
        ObjectNode node = mapper.createObjectNode();
        node.put("categoria", bc.getCategory());
        node.put("pregunta", bc.getQuestionExample());
        node.put("respuesta", bc.getResponse());
        return node;
    }
}
