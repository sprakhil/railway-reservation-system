package com.railway.pnrservice.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PnrResponse {
    private String pnrNumber;
    private LocalDateTime generatedAt;
}