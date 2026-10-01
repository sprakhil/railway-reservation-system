package com.railway.authservice.controller;
import com.railway.authservice.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@RestController @RequestMapping("/api/auth/admin/users") @RequiredArgsConstructor
@Tag(name="Admin users",description="ADMIN only. No credentials are returned. Blocking revokes sessions; role changes remain invitation-controlled.")
public class AdminUserController {
    private final AdminUserService users;
    @GetMapping public AdminUserService.UserPage list(Authentication a,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String role,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="id") String sort,@RequestParam(defaultValue="desc") String direction){return users.list(a.getName(),search,status,role,page,size,sort,direction);}
    @GetMapping("/stats") public Map<String,Long> stats(Authentication a){return users.stats(a.getName());}
    public record StatusRequest(String status){}
    @PutMapping("/{username}/status") @Operation(summary="Block or reactivate a passenger; current sessions are revoked")
    public AdminUserService.UserView status(Authentication a,@PathVariable String username,@RequestBody StatusRequest request){return users.status(a.getName(),username,request.status());}
    @GetMapping(value="/export",produces="text/csv") @Operation(summary="Export filtered users as Excel-compatible CSV; maximum 10000 rows")
    public ResponseEntity<String> export(Authentication a,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String role){return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8")).header("Content-Disposition","attachment; filename=railwize-users.csv").header("Cache-Control","no-store").body(users.csv(a.getName(),search,status,role));}
}
