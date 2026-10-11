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
        ensureRun(key.substring(0,key.lastIndexOf('|')));
        // Both MySQL and the test database's MySQL mode support this atomic first-use insert.
        jdbc.update("insert ignore into seat_inventory (inventory_key,capacity) values (?,?)", key, capacity);
    }
    public static String runKey(String train,java.time.LocalDate date){return train+"|"+date;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void ensureRun(String runKey) {
        String lockKey=runKey+"|RUN";
        // Read first: repeated calls inside an outer transaction may already hold this lock.
        if(jdbc.queryForObject("select count(*) from seat_inventory where inventory_key=?",Long.class,lockKey)==0)
            jdbc.update("insert ignore into seat_inventory (inventory_key,capacity) values (?,0)",lockKey);
    }
}
