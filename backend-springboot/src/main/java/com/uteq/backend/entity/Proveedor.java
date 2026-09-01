package com.uteq.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "proveedores")
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, unique = true, length = 150)
    private String nombre;

    @Size(max = 20)
    @Column(length = 20)
    private String ruc;

    @Size(max = 255)
    @Column(length = 255)
    private String direccion;

    @Size(max = 30)
    @Column(length = 30)
    private String telefono;

    @Email
    @Size(max = 150)
    @Column(length = 150)
    private String email;

    @Size(max = 150)
    @Column(name = "persona_contacto", length = 150)
    private String personaContacto;

    @Column(nullable = false)
    private Boolean activo = true;
}
