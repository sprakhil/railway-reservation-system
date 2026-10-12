package com.railway.reservationservice.dto;
import com.railway.reservationservice.entity.SupportTicket.*;
import jakarta.validation.constraints.*;
import java.time.Instant;

public final class SupportTypes {
    private SupportTypes() {}
    public record Create(@NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") String requestId,
        @NotNull Category category,@NotBlank @Size(max=160) String subject,
        @Pattern(regexp="[0-9]{10}") String pnr,@NotBlank @Size(max=3000) String message) {}
    public record Reply(@NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") String requestId,
        @NotBlank @Size(max=3000) String message) {}
    public record ChangeStatus(@NotNull Status status,@NotNull @Min(0) Long version) {}
    public record Ticket(String id,String subject,Category category,Status status,String pnr,
        String username,String email,Instant createdAt,Instant updatedAt,long version) {}
    public record Message(String id,String author,boolean staff,boolean systemMessage,String body,Instant createdAt) {}
}
