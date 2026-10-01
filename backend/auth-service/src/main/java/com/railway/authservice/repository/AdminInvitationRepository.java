package com.railway.authservice.repository;
import com.railway.authservice.entity.AdminInvitation; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.Optional;
public interface AdminInvitationRepository extends JpaRepository<AdminInvitation,Long> {
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select i from AdminInvitation i where i.tokenHash=:hash") Optional<AdminInvitation> lockHash(@Param("hash") String hash);
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select i from AdminInvitation i where i.id=:id") Optional<AdminInvitation> lockId(@Param("id") Long id);
}
