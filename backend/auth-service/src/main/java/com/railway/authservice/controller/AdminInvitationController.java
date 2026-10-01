package com.railway.authservice.controller;
import com.railway.authservice.service.AdminInvitationService; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*; import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.util.Map;
@RestController @RequiredArgsConstructor
public class AdminInvitationController {
 private final AdminInvitationService invitations;
 public record Invite(@NotBlank @Email @Size(max=254) String email){}
 public record Accept(@NotBlank @Size(min=40,max=100) String token){}
 @PostMapping("/api/auth/admin/invitations") @ResponseStatus(HttpStatus.CREATED) public AdminInvitationService.InvitationView invite(Authentication auth,@Valid @RequestBody Invite req){return invitations.invite(auth.getName(),req.email());}
 @GetMapping("/api/auth/admin/invitations") public Map<String,Object> list(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return invitations.list(auth.getName(),page,size);}
 @DeleteMapping("/api/auth/admin/invitations/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void revoke(Authentication auth,@PathVariable Long id){invitations.revoke(auth.getName(),id);}
 @PostMapping("/api/auth/admin-invitations/accept") public Map<String,String> accept(Authentication auth,@Valid @RequestBody Accept req){invitations.accept(auth.getName(),req.token()); return Map.of("message","Administrator access granted. Sign in again to continue.");}
}
