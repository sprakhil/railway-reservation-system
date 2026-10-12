package com.railway.reservationservice.controller;
import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.dto.SupportTypes.*;
import com.railway.reservationservice.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@RestController @RequestMapping("/api/reservations/admin/support") @RequiredArgsConstructor
@Tag(name="Admin support",description="ADMIN only. Search requests, reply and update status with an audit trail.")
public class AdminSupportController {
    private final SupportService support;
    @GetMapping public PageResult<Ticket> list(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String status,@RequestParam(required=false) String category,@RequestParam(required=false) String search){return support.list(auth.getName(),true,page,size,status,category,search);}
    @GetMapping("/{id}") public Ticket get(Authentication auth,@PathVariable String id){return support.get(id,auth.getName(),true);}
    @GetMapping("/{id}/messages") public PageResult<Message> messages(Authentication auth,@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return support.conversation(id,auth.getName(),true,page,size);}
    @PostMapping("/{id}/messages") public Ticket reply(Authentication auth,@PathVariable String id,@Valid @RequestBody Reply body){return support.reply(id,auth.getName(),true,body);}
    @PutMapping("/{id}/status") @Operation(summary="Update support status",description="Supply the current ticket version. A stale version returns 409. Status changes are recorded in the conversation.")
    public Ticket status(Authentication auth,@PathVariable String id,@Valid @RequestBody ChangeStatus body){return support.status(id,auth.getName(),body);}
}
