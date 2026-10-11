package com.railway.reservationservice.repository;
import com.railway.reservationservice.entity.TrainRunCancellation;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TrainRunCancellationRepository extends JpaRepository<TrainRunCancellation,String> {}
