package com.uteq.backend.dto;
import com.fasterxml.jackson.annotation.JsonProperty;


// Forma común de respuesta para pagar/anular: ambos SPs (sp_pagar_multa,
// sp_anular_multa) retornan el mismo par de OUT params
// (o_multa_id, o_usuario_desbloqueado) -- confirmado en
// docs/basedatos/CATALOGO-SP.md y en db/procs/sp_pagar_multa.sql /
// sp_anular_multa.sql.
public record FineActionResponseDTO( @JsonProperty("multaId") Long fineId, @JsonProperty("usuarioDesbloqueado") Boolean userUnblocked
) {}