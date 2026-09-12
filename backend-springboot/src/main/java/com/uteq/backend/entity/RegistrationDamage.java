package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "registro_danos")
public class RegistrationDamage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("prestamoId")
    @Column(name = "prestamo_id", nullable = false)  private Long loanId;

    @Column(name = "estado_devolucion", nullable = false, length = 20)
    private String statusLoanReturn;

    @JsonProperty("descripcion")
    @Column(name = "descripcion", columnDefinition = "TEXT")  private String description;

    @Column(name = "bibliotecario_id", nullable = false)
    private Long librarianId;

    @JsonProperty("fechaRegistro")
    @Column(name = "fecha_registro", nullable = false)  private OffsetDateTime dateRegistration;
}
