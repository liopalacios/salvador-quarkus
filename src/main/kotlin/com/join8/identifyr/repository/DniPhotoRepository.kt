package com.join8.identifyr.repository

import com.join8.identifyr.model.entity.DniPhoto
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface DniPhotoRepository : JpaRepository<DniPhoto, UUID> {
    // DELETE directo (sin cargar las imágenes); corre antes de insertar las nuevas por el índice único (user_id, side)
    @Modifying(flushAutomatically = true)
    @Query("delete from DniPhoto p where p.userId = :userId")
    fun deleteByUserId(userId: UUID): Int

    fun findAllByUserId(userId: UUID): List<DniPhoto>
}
