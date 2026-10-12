package com.railway.reservationservice.controller;

import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.dto.SupportTypes.*;
import com.railway.reservationservice.service.SupportService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@RestController @RequestMapping("/api/reservations/support") @RequiredArgsConstructor
@Tag(name="Support requests",description="Private account-owned requests. Optional PNR must belong to the signed-in account. No attachments or financial actions.")
public class SupportController {
    private final SupportService support;
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Create a support request",description="Use a fresh UUID requestId; reuse the same ID/body on retry. Category: BOOKING, PAYMENT_REFUND, ACCOUNT or OTHER.")
    public Ticket create(Authentication auth,HttpServletRequest http,@Valid @RequestBody Create body){return support.create(auth.getName(),(String)http.getAttribute("loggedInUserEmail"),body);}
    @GetMapping public PageResult<Ticket> list(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String status,@RequestParam(required=false) String category,@RequestParam(required=false) String search){return support.list(auth.getName(),false,page,size,status,category,search);}
    @GetMapping("/{id}") public Ticket get(Authentication auth,@PathVariable String id){return support.get(id,auth.getName(),false);}
    @GetMapping("/{id}/messages") public PageResult<Message> messages(Authentication auth,@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return support.conversation(id,auth.getName(),false,page,size);}
    @PostMapping("/{id}/messages") public Ticket reply(Authentication auth,@PathVariable String id,@Valid @RequestBody Reply body){return support.reply(id,auth.getName(),false,body);}
}
