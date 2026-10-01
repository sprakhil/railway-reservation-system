package com.railway.reservationservice.service;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SeatLayoutsTest {
    @Test void usesClassSpecificCoachNames(){
        Map.of("SL","S","CLASS_3A","B","CLASS_2A","A","CLASS_1A","H",
            "CLASS_3E","M","EC","E","CLASS_2S","D","UR","GS").forEach((clazz,prefix)->{
            var seat=SeatLayouts.describe(clazz,clazz+"-C2-12");
            assertEquals(prefix+"2",seat.coach());assertEquals(12,seat.position());
            assertFalse(seat.description().contains("C2"));
        });
    }
    @Test void threeTierRepeatsEightPositionsAndResetsForEachCoach(){
        String[] types={"LOWER","MIDDLE","UPPER","LOWER","MIDDLE","UPPER","SIDE_LOWER","SIDE_UPPER"};
        for(String clazz:new String[]{"SL","CLASS_3A"})for(int coach=1;coach<=2;coach++)for(int n=1;n<=24;n++){
            var seat=SeatLayouts.describe(clazz,clazz+"-C"+coach+"-"+n);
            assertEquals(types[(n-1)%8],seat.berthType());assertEquals((n-1)/8+1,seat.bay());
            assertEquals("THREE_TIER_8_V1",seat.layout());
        }
        assertEquals("Coach S1 · Berth 7 · Side lower berth",SeatLayouts.describe("SL","SL-C1-7").description());
    }
    @Test void twoTierHasNoMiddleBerth(){
        String[] types={"LOWER","UPPER","LOWER","UPPER","SIDE_LOWER","SIDE_UPPER"};
        for(int n=1;n<=18;n++){
            var seat=SeatLayouts.describe("CLASS_2A","CLASS_2A-C1-"+n);
            assertEquals(types[(n-1)%6],seat.berthType());assertEquals((n-1)/6+1,seat.bay());
            assertEquals("TWO_TIER_6_V1",seat.layout());
        }
    }
    @Test void doesNotInventBerthTypesForSeatsOrUnconfiguredLayouts(){
        for(String clazz:new String[]{"EC","CLASS_2S","UR"}){
            var seat=SeatLayouts.describe(clazz,clazz+"-C1-1");assertEquals("SEAT",seat.berthType());assertNull(seat.bay());
        }
        for(String clazz:new String[]{"CLASS_1A","CLASS_3E"}){
            var seat=SeatLayouts.describe(clazz,clazz+"-C1-1");assertNull(seat.berthType());assertNull(seat.layout());
            assertTrue(seat.description().contains("Position not specified"));
        }
    }
    @Test void unassignedLegacyAndInvalidIdsAreNeverGuessed(){
        assertEquals("Not assigned",SeatLayouts.describe("SL",null).description());
        for(String raw:new String[]{"B1-26","SL-C0-1","SL-C31-1","SL-C1-101","SL-C1-0","CLASS_2A-C1-1"}){
            var seat=SeatLayouts.describe("SL",raw);assertNull(seat.coach());assertEquals(raw,seat.description());
        }
        assertNull(SeatLayouts.describe("FUTURE","FUTURE-C1-1").coach());
        assertEquals("B1",SeatLayouts.describe("3A","CLASS_3A-C1-1").coach());
    }
}
