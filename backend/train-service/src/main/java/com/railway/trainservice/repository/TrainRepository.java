package com.railway.trainservice.repository;

import com.railway.trainservice.entity.Train;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface TrainRepository extends JpaRepository<Train, Long> {
    Optional<Train> findByTrainNumber(String trainNumber);
    boolean existsByTrainNumber(String trainNumber);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Train t where t.trainNumber = :number")
    Optional<Train> findForUpdate(@Param("number") String trainNumber);
}
