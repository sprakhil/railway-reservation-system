package com.railway.userservice.controller;
import com.railway.userservice.dto.*; import com.railway.userservice.entity.SavedPassenger; import com.railway.userservice.service.SavedPassengerService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*; import org.springframework.security.core.Authentication; import org.springframework.http.HttpStatus; import jakarta.validation.Valid;
@RestController @RequestMapping("/api/users/me/passengers") @RequiredArgsConstructor
public class SavedPassengerController {
 private final SavedPassengerService passengers;
 @GetMapping public PageResult<SavedPassenger> list(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return passengers.list(auth.getName(),page,size);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public SavedPassenger add(Authentication auth,@Valid @RequestBody SavedPassengerRequest req){return passengers.save(auth.getName(),null,req);}
 @PutMapping("/{id}") public SavedPassenger edit(Authentication auth,@PathVariable Long id,@Valid @RequestBody SavedPassengerRequest req){return passengers.save(auth.getName(),id,req);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(Authentication auth,@PathVariable Long id){passengers.delete(auth.getName(),id);}
}
