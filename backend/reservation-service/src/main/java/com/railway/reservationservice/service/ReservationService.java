package com.railway.reservationservice.service;

import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.exception.BookingException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class ReservationService {
    private final JourneyService journeys;
    private final InventoryInitializer inventory;
    private final BookingEngine engine;
    private final PnrGateway pnrs;

    public ReservationRes bookTicket(String username,String email,String idempotencyKey,ReservationReq req) {
        if(req==null || req.getPassengers()==null || req.getPassengers().isEmpty()
                || req.getPassengers().size()>6 || req.getPassengers().stream().anyMatch(Objects::isNull))
            throw BookingException.badRequest("Provide 1 to 6 non-null passengers");
        if(idempotencyKey==null || !idempotencyKey.matches("[A-Za-z0-9_-]{8,80}"))
            throw BookingException.badRequest("Idempotency-Key must contain 8 to 80 letters, numbers, underscores or hyphens");
        String key=username+":"+idempotencyKey;
        String hash=fingerprint(req);
        ReservationRes previous=engine.replay(key,hash);
        if(previous!=null) return previous;
        JourneyQuote quote=journeys.quote(req.getTrainNumber(),req.getFromStation(),req.getToStation(),req.getDateOfJourney(),req.getSeatClass(),req.getPassengers().size());
        if(req.getExpectedTotalFare()==null || req.getExpectedTotalFare().compareTo(quote.getTotalFare())!=0)
            throw BookingException.conflict("Fare changed or does not match the quote. Fetch a new quote before booking.");
        inventory.ensure(quote.inventoryKey(),quote.capacity());
        String pnr=pnrs.generatePnr();
        try { return engine.create(username,email,key,hash,pnr,req,quote); }
        catch(DataIntegrityViolationException ex) {
            ReservationRes concurrent=engine.replay(key,hash);
            if(concurrent!=null) return concurrent;
            throw BookingException.conflict("Booking conflicted with another request. Retry with the same idempotency key.");
        }
    }

    public JourneyQuote quote(String train,String from,String to,LocalDate date,String seatClass,int count) {
        JourneyQuote quote=journeys.quote(train,from,to,date,seatClass,count);
        engine.describeAvailability(quote); return quote;
    }
    public List<JourneyQuote> search(String from,String to,LocalDate date,String seatClass,int count) {
        return journeys.search(from,to,date,seatClass,count).stream().peek(engine::describeAvailability).toList();
    }
    public List<TrainSearchResult> discover(String from,String to,LocalDate date) {
        Map<String,List<JourneyQuote>> grouped=new LinkedHashMap<>();
        for(var quote:journeys.searchAllClasses(from,to,date)) {
            engine.describeAvailability(quote);
            grouped.computeIfAbsent(quote.getTrainNumber(),key->new ArrayList<>()).add(quote);
        }
        return grouped.values().stream().map(classes->new TrainSearchResult(classes.getFirst().getTrainNumber(),classes.getFirst().getTrainName(),List.copyOf(classes))).toList();
    }
    public List<ReservationRes> getMyBookings(String username) { return engine.mine(username); }
    public BookingOverview overview(String username) { return engine.overview(username); }
    public ReservationRes getReservationByPnr(String pnr,String username,boolean admin) { return engine.get(pnr,username,admin); }
    public CancellationResponse cancelReservation(String pnr,String username,boolean admin) { return engine.cancel(pnr,username,admin); }

    public PageResult<ReservationRes> history(String username,int page,int size,String sort,String direction,String status,LocalDate from,LocalDate to) {
        return engine.history(username,page,size,sort,direction,status,from,to);
    }
    public CancellationResponse preview(String pnr,String username,boolean admin){return engine.preview(pnr,username,admin);}
    private String fingerprint(ReservationReq req) {
        StringBuilder input=new StringBuilder();
        add(input,req.getTrainNumber()); add(input,req.getFromStation()); add(input,req.getToStation()); add(input,req.getSeatClass());
        add(input,req.getDateOfJourney()); add(input,req.getExpectedTotalFare()==null?null:req.getExpectedTotalFare().stripTrailingZeros());
        for(PassengerDto p:req.getPassengers()) { add(input,p.getName()); add(input,p.getAge()); add(input,p.getGender()); add(input,p.getBirthPreference()); }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private void add(StringBuilder output,Object value) { String text=String.valueOf(value); output.append(text.length()).append(':').append(text); }
}
