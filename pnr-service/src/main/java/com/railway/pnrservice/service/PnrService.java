package com.railway.pnrservice.service;

import com.railway.pnrservice.entity.PnrRecord;
import com.railway.pnrservice.repository.PnrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class PnrService {

    private final PnrRepository pnrRepository;
    private final Random random = new Random();

    @Transactional
    public String generateUniquePnr() {
        String pnr;
        boolean isUnique = false;

        // Loop guarantees absolute uniqueness against the database
        do {
            pnr = generate10DigitNumber();
            isUnique = !pnrRepository.existsByPnrNumber(pnr);
        } while (!isUnique);

        PnrRecord record = PnrRecord.builder()
                .pnrNumber(pnr)
                .generatedAt(LocalDateTime.now())
                .build();

        pnrRepository.save(record);
        log.info("Generated new unique PNR: {}", pnr);

        return pnr;
    }

    private String generate10DigitNumber() {
        // Generates a random number between 1000000000 and 9999999999
        long number = 1000000000L + (long) (random.nextDouble() * 9000000000L);
        return String.valueOf(number);
    }
}