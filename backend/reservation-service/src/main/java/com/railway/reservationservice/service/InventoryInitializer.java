package com.railway.reservationservice.service;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @RequiredArgsConstructor
public class InventoryInitializer {
    private final JdbcTemplate jdbc;
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void ensure(String key,int capacity) {
        // Both MySQL and the test database's MySQL mode support this atomic first-use insert.
        jdbc.update("insert ignore into seat_inventory (inventory_key,capacity) values (?,?)", key, capacity);
    }
}
