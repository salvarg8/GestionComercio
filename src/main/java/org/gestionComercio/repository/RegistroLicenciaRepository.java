package org.gestionComercio.repository;

import org.gestionComercio.entity.RegistroLicencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface RegistroLicenciaRepository extends JpaRepository<RegistroLicencia, Long> {

    /** Registra la fecha de uso sólo si es posterior a la guardada (nunca la hace retroceder). */
    @Modifying
    @Query("update RegistroLicencia r set r.ultimaFecha = :hoy where r.id = :id and r.ultimaFecha < :hoy")
    int registrarUso(@Param("id") Long id, @Param("hoy") LocalDate hoy);

}
