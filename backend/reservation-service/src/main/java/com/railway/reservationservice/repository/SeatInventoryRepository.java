package com.railway.reservationservice.repository;
import com.railway.reservationservice.entity.SeatInventory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface SeatInventoryRepository extends JpaRepository<SeatInventory,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SeatInventory i where i.inventoryKey = :key")
    Optional<SeatInventory> lock(@Param("key") String key);
}
