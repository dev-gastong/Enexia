package com.enexia.rg.model;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class MiembrosOrganizacionId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "id_persona_juridica")
    private Long idPersonaJuridica;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MiembrosOrganizacionId that = (MiembrosOrganizacionId) o;
        return Objects.equals(idUsuario, that.idUsuario)
                && Objects.equals(idPersonaJuridica, that.idPersonaJuridica);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idUsuario, idPersonaJuridica);
    }
}
