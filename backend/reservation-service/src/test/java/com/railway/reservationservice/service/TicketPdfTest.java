package com.railway.reservationservice.service;

import com.railway.reservationservice.dto.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.*;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketPdfTest {
    private ReservationRes booking(String state,boolean longNames) {
        return ReservationRes.builder().pnr("7824519036").trainNumber("12133").seatClass("CLASS_3A")
            .fromStation("CSMT").toStation("MAO").dateOfJourney(LocalDate.of(2030,1,22))
            .departureAt(Instant.parse("2030-01-22T14:40:00Z")).waitlistDeadline(Instant.parse("2030-01-22T13:40:00Z"))
            .status(state).paymentStatus("CAPTURED").waitlistPosition(state.equals("WAITING")?3:null)
            .farePerPassenger(new BigDecimal("1240.00")).totalFare(new BigDecimal("7440.00"))
            .refundAmount(BigDecimal.ZERO).refundStatus("NONE").passengers(java.util.stream.IntStream.range(0,6).mapToObj(i->PassengerDto.builder()
                .name(longNames?"Alexandria".repeat(11)+i:List.of("Aarav Sharma","Ananya Deshmukh","Rohan Mehta","Ishita Nair","Élodie Fernandes","Kabir Rao").get(i))
                .age(24+i).gender(i%2==0?"MALE":"FEMALE").bookingStatus(state)
                .seatNumber(state.equals("WAITING")?null:"B1-"+(21+i)).build()).toList()).build();
    }
    private void verifyAndPreview(ReservationRes r,String name)throws Exception {
        var engine=mock(BookingEngine.class);var service=new TicketPdfService(engine);
        when(engine.get(r.getPnr(),"owner",false)).thenReturn(r);
        byte[] pdf=service.download(r.getPnr(),"owner",false);
        try(var doc=Loader.loadPDF(pdf)) {
            String text=new PDFTextStripper().getText(doc);
            assertTrue(text.contains("railwize."));assertTrue(text.contains("Payment mode: Razorpay TEST"));
            assertTrue(text.contains(r.getPnr()));assertTrue(text.contains(r.getStatus()));assertTrue(text.contains("INR 7440.00"));
            assertFalse(text.contains("NOT VALID FOR TRAVEL"));assertTrue(text.contains("20:10 IST"));
            assertTrue(doc.getNumberOfPages()<=3);
            if(!name.contains("long-names"))assertEquals(1,doc.getNumberOfPages());
            if(name.contains("berth-layout")){
                assertTrue(text.contains("S1 / Berth 7"));assertTrue(text.contains("Side lower berth"));
                assertTrue(text.contains("S2 / Berth 1"));assertTrue(text.contains("Middle berth"));
                assertFalse(text.contains("SL-C"));
            }
            else if(!r.getStatus().equals("WAITING"))assertTrue(text.contains("B1-26"));
            else {assertTrue(text.contains("POSITION 3"));assertTrue(text.contains("Not assigned"));}
            new PDFTextStripper(){@Override protected void processTextPosition(TextPosition p){
                assertTrue(p.getXDirAdj()>=30 && p.getXDirAdj()+p.getWidthDirAdj()<=566,"Horizontal overflow: "+p.getUnicode());
                assertTrue(p.getYDirAdj()>20 && p.getYDirAdj()<825,"Vertical overflow: "+p.getUnicode());
                super.processTextPosition(p);
            }}.getText(doc);
            Path folder=Path.of("target/pdf-preview");Files.createDirectories(folder);Files.write(folder.resolve(name+".pdf"),pdf);
            for(int i=0;i<doc.getNumberOfPages();i++)javax.imageio.ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(i,120),"png",folder.resolve(name+"-"+(i+1)+".png").toFile());
        }
        r.setPaymentStatus("UNPAID");assertThrows(RuntimeException.class,()->service.download(r.getPnr(),"owner",false));
    }
    @Test void confirmedTicketIsReadableAndRendered()throws Exception{verifyAndPreview(booking("CONFIRMED",false),"railwize-confirmed");}
    @Test void waitlistTicketHasNoInventedSeatsAndShowsCutoff()throws Exception{verifyAndPreview(booking("WAITING",false),"railwize-waitlisted");}
    @Test void longNamesWrapAndPaginateWithoutOverflow()throws Exception{verifyAndPreview(booking("CONFIRMED",true),"railwize-long-names");}
    @Test void classSpecificCoachAndBerthsFitOnTicket()throws Exception{
        var r=booking("CONFIRMED",false);r.setSeatClass("SL");
        String[] seats={"SL-C1-1","SL-C1-2","SL-C1-3","SL-C1-7","SL-C1-8","SL-C2-1"};
        for(int i=0;i<seats.length;i++)r.getPassengers().get(i).setSeatNumber(seats[i]);
        verifyAndPreview(r,"railwize-berth-layout");
    }
}
