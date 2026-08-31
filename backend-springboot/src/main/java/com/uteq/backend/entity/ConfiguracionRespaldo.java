package com.uteq.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "configuracion_respaldo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionRespaldo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "habilitado")
    private Boolean habilitado;

    @Column(name = "frecuencia_horas", nullable = false)
    private Integer frecuenciaHoras;

    @Column(name = "dias_retencion", nullable = false)
    private Integer diasRetencion;

    @Column(name = "ultima_ejecucion")
    private OffsetDateTime ultimaEjecucion;

    @Column(name = "proxima_ejecucion")
    private OffsetDateTime proximaEjecucion;

    @Column(name = "actualizado_por")
    private Long actualizadoPor;

    @Column(name = "actualizado_en", insertable = false, updatable = false)
    private OffsetDateTime actualizadoEn;

    public boolean isHabilitado() {
        return habilitado != null && habilitado;
    }
}
