package com.railway.userservice.repository;
import com.railway.userservice.entity.SavedPassenger; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.domain.*; import java.util.Optional;
public interface SavedPassengerRepository extends JpaRepository<SavedPassenger,Long> {
 Page<SavedPassenger> findByUsername(String username,Pageable pageable);
 Optional<SavedPassenger> findByIdAndUsername(Long id,String username);
}
