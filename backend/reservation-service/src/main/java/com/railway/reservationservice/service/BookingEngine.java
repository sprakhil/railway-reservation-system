package com.railway.reservationservice.service;
import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.entity.*;
import com.railway.reservationservice.exception.BookingException;
import com.railway.reservationservice.repository.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.railway.reservationservice.entity.Reservation.ReservationStatus.*;

@Service @RequiredArgsConstructor
public class BookingEngine {
    private final ReservationRepository reservations;
    private final SeatInventoryRepository inventories;
    private final BookingNotificationRepository notifications;
    private final NotificationCenter notificationCenter;
    private final Clock clock;
    private final InventoryInitializer initializer;
    private final TrainRunCancellationRepository cancelledRuns;
    @PersistenceContext private EntityManager entityManager;
    @Value("${booking.hold-duration:PT2M}") private Duration holdDuration=Duration.ofMinutes(2);
    @Value("${booking.waitlist-cutoff:PT1H}") private Duration waitlistCutoff=Duration.ofHours(1);
    @Value("${booking.cancellation-rate:0.20}") private BigDecimal cancellationRate=new BigDecimal("0.20");

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public ReservationRes replay(String key,String hash) {
        return reservations.findByBookingKey(key).map(r->{Reservation current=locked(r.getPnr()); checkReplay(current,hash); maintain(current.getInventoryKey()); return map(current);}).orElse(null);
    }
    private void checkReplay(Reservation r,String hash) {
        if(!Objects.equals(r.getRequestHash(),hash)) throw BookingException.conflict("Idempotency key was already used for a different booking");
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public ReservationRes create(String username,String email,String key,String hash,String pnr,ReservationReq req,JourneyQuote quote) {
        SeatInventory inventory=lockInventory(quote.inventoryKey());
        Optional<Reservation> previous=reservations.findByBookingKey(key);
        if(previous.isPresent()){checkReplay(previous.get(),hash); maintain(quote.inventoryKey()); return map(previous.get());}
        if(cancelledRuns.existsById(InventoryInitializer.runKey(quote.getTrainNumber(),quote.getRunDate())))
            throw BookingException.conflict("This train run has been cancelled by the operator");
        if(inventory.getCapacity()!=quote.capacity()) throw BookingException.conflict("Inventory configuration changed; contact the administrator");
        int count=req.getPassengers().size();
        if(count>inventory.getCapacity()) throw BookingException.badRequest("Passenger count exceeds the entire train class capacity");
        Instant now=clock.instant();
        if(!quote.getDepartureAt().isAfter(now)) throw BookingException.conflict("Boarding time has passed");
        if(holdDuration.isNegative() || holdDuration.isZero() || waitlistCutoff.isNegative()) throw new IllegalStateException("Invalid booking deadlines");
        maintain(quote.inventoryKey());
        List<String> seats=available(quote.inventoryKey(),quote.getFromSequence(),quote.getToSequence(),quote.getSeatClass(),quote.getCoachCount(),quote.getSeatsPerCoach());
        boolean waiting=!waiting(quote.inventoryKey()).isEmpty() || seats.size()<count;
        Instant deadline=quote.getDepartureAt().minus(waitlistCutoff);
        if(waiting && !deadline.isAfter(now)) throw BookingException.conflict("Waitlist booking has closed for this journey");
        Instant expiry=now.plus(holdDuration), limit=waiting?deadline:quote.getDepartureAt();
        if(expiry.isAfter(limit)) expiry=limit;
        Reservation r=Reservation.builder().pnr(pnr).username(username).userEmail(email)
            .trainName(quote.getTrainName()).trainNumber(quote.getTrainNumber()).dateOfJourney(quote.getBoardingDate()).runDate(quote.getRunDate())
            .fromStation(quote.getFromStation()).toStation(quote.getToStation()).seatClass(quote.getSeatClass())
            .fromSequence(quote.getFromSequence()).toSequence(quote.getToSequence()).distanceKm(quote.getDistanceKm())
            .departureAt(quote.getDepartureAt()).inventoryKey(quote.inventoryKey()).bookingTime(LocalDateTime.ofInstant(now,ZoneOffset.UTC))
            .holdExpiresAt(expiry).waitlistDeadline(deadline).waitlistRequested(waiting).coachCount(quote.getCoachCount()).seatsPerCoach(quote.getSeatsPerCoach())
            .totalFare(quote.getTotalFare()).farePerPassenger(quote.getFarePerPassenger()).currency("INR")
            .baseFare(quote.getBaseFare()).ratePerKm(quote.getRatePerKm()).minimumFare(quote.getMinimumFare())
            .cancellationFee(BigDecimal.ZERO).refundAmount(BigDecimal.ZERO).refundStatus("NONE")
            .bookingKey(key).requestHash(hash).dataVersion(2).status(PENDING_PAYMENT).passengers(new ArrayList<>()).build();
        for(int i=0;i<count;i++) {
            PassengerDto p=req.getPassengers().get(i);
            r.addPassenger(Passenger.builder().name(p.getName().trim()).age(p.getAge()).gender(p.getGender()).birthPreference(p.getBirthPreference())
                .seatNumber(waiting?null:seats.get(i)).bookingStatus(Passenger.BookingStatus.PENDING_PAYMENT).build());
        }
        reservations.saveAndFlush(r);
        notificationCenter.publish(r,"BOOKING_HELD","Complete payment before the hold expires at "+expiry.atZone(ZoneId.of("Asia/Kolkata")).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss 'IST'"))+". Open your booking to check the remaining time.");
        return map(r);
    }
    @Transactional(readOnly=true) public int availability(JourneyQuote q) {
        return available(q.inventoryKey(),q.getFromSequence(),q.getToSequence(),q.getSeatClass(),q.getCoachCount(),q.getSeatsPerCoach()).size();
    }
    @Transactional(readOnly=true) public void describeAvailability(JourneyQuote q) {
        q.setAvailableSeats(availability(q));
        q.setWaitlistDeadline(q.getDepartureAt().minus(waitlistCutoff));
        q.setWaitlistOpen(q.getPassengerCount()<=q.capacity() && q.getWaitlistDeadline().isAfter(clock.instant()));
        boolean queued=waiting(q.inventoryKey()).stream().anyMatch(r->r.getWaitlistDeadline()!=null && r.getWaitlistDeadline().isAfter(clock.instant()));
        q.setSuggestedAllocation(!queued && q.getAvailableSeats()>=q.getPassengerCount()?"CNF":q.isWaitlistOpen()?"WL":"UNAVAILABLE");
    }
    private List<String> available(String key,int from,int to,String seatClass,int coaches,int seats) {
        Set<String> occupied=new HashSet<>();
        reservations.activeOverlapping(key,from,to,clock.instant()).forEach(r->r.getPassengers().forEach(p->{if(p.getSeatNumber()!=null) occupied.add(p.getSeatNumber());}));
        List<String> result=new ArrayList<>();
        for(int c=1;c<=coaches;c++) for(int s=1;s<=seats;s++){String label=seatClass+"-C"+c+"-"+s; if(!occupied.contains(label)) result.add(label);}
        return result;
    }
    private List<Reservation> waiting(String key) {return reservations.findByInventoryKeyAndStatusInOrderByReservationIdAsc(key,List.of(WAITING)).stream().filter(r->r.getDataVersion()>=2 && r.getRazorpayPaymentId()!=null).toList();}
    // All transitions lock inventory before reservation. Refresh avoids stale state after waiting for the lock.
    private Reservation locked(String pnr) {
        Reservation r=reservations.findByPnr(pnr).orElseThrow(()->new BookingException(HttpStatus.NOT_FOUND,"Booking not found"));
        if(r.getInventoryKey()!=null) lockInventory(r.getInventoryKey());
        entityManager.refresh(r,LockModeType.PESSIMISTIC_WRITE); return r;
    }
    private SeatInventory lockInventory(String key) {
        lockRun(key.substring(0,key.lastIndexOf('|')));
        return inventories.lock(key).orElseThrow(()->BookingException.conflict("Inventory is not initialized"));
    }
    private void lockRun(String key) {
        initializer.ensureRun(key);
        inventories.lock(key+"|RUN").orElseThrow(()->BookingException.conflict("Run inventory is not initialized"));
    }
    // Caller holds the inventory lock. Expire before promotion; never partially allocate or skip a paid queue head.
    private void maintain(String key) {
        if(key==null) return;
        // Operator cancellation never releases inventory for promotion or further sales.
        if(cancelledRuns.existsById(key.substring(0,key.lastIndexOf('|')))) return;
        Instant now=clock.instant();
        for(Reservation r:reservations.findByInventoryKeyAndStatusInOrderByReservationIdAsc(key,List.of(PENDING_PAYMENT,WAITING))) {
            if(r.getStatus()==PENDING_PAYMENT && !r.getHoldExpiresAt().isAfter(now)) {
                setStatus(r,EXPIRED); event(r,"PAYMENT_INCOMPLETE","Payment was not confirmed before the booking hold expired. The seats have been released.");
            }
            else if(r.getStatus()==WAITING && r.getWaitlistDeadline()!=null && !r.getWaitlistDeadline().isAfter(now))
                cancelPaid(r,BigDecimal.ZERO,"WAITLIST_EXPIRED","Waitlist deadline reached. Your whole booking was cancelled.");
        }
        reservations.flush();
        for(Reservation r:waiting(key)) {
            if(r.getDataVersion()<2 || r.getRazorpayPaymentId()==null) continue;
            List<String> seats=available(key,r.getFromSequence(),r.getToSequence(),r.getSeatClass(),r.getCoachCount(),r.getSeatsPerCoach());
            if(seats.size()<r.getPassengers().size()) break;
            for(int i=0;i<r.getPassengers().size();i++) r.getPassengers().get(i).setSeatNumber(seats.get(i));
            setStatus(r,CONFIRMED); reservations.flush();
            event(r,"PROMOTED","Your entire waitlisted booking is now confirmed. Seats: "+seatSummary(r));
        }
    }
    private void setStatus(Reservation r,Reservation.ReservationStatus status){r.setStatus(status); r.getPassengers().forEach(p->p.setBookingStatus(Passenger.BookingStatus.valueOf(status.name())));}
    private String seatSummary(Reservation r){return String.join(", ",r.getPassengers().stream().map(Passenger::getSeatNumber).filter(Objects::nonNull).map(seat->SeatLayouts.describe(r.getSeatClass(),seat).description()).toList());}
    private void event(Reservation r,String type,String message) {
        notificationCenter.publish(r,type,message);
        if(r.getUserEmail()==null || r.getUserEmail().isBlank()) return;
        String id=r.getPnr()+":"+type;
        if(notifications.existsById(id)) return;
        BookingNotification e=new BookingNotification(); e.setEventId(id); e.setPnr(r.getPnr()); e.setEventType(type); e.setMessage(message);
        e.setUsername(r.getUsername()); e.setUserEmail(r.getUserEmail()); e.setTrainNumber(r.getTrainNumber()); e.setDateOfJourney(r.getDateOfJourney().toString());
        e.setBookingJson(new tools.jackson.databind.json.JsonMapper().writeValueAsString(map(r)));
        e.setNextAttemptAt(clock.instant()); notifications.save(e);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public ReservationRes get(String pnr,String username,boolean admin){Reservation r=locked(pnr); authorize(r,username,admin); maintain(r.getInventoryKey()); return map(r);}
    @Transactional(readOnly=true) public List<ReservationRes> mine(String username){return reservations.findByUsername(username).stream().map(this::map).toList();}
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public BookingOverview overview(String username) {
        reservations.userMaintenanceKeys(username).forEach(key->{lockInventory(key);maintain(key);});
        var counts=new LinkedHashMap<String,Long>();
        for(var status:Reservation.ReservationStatus.values()) counts.put(status.name(),reservations.countByUsernameAndStatus(username,status));
        var next=reservations.findFirstByUsernameAndStatusInAndDepartureAtAfterOrderByDepartureAtAscReservationIdAsc(
            username,List.of(CONFIRMED,WAITING),clock.instant()).map(this::map).orElse(null);
        return new BookingOverview(reservations.countByUsername(username),counts,next);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public PageResult<ReservationRes> history(String username,int page,int size,String sort,String direction,String status,LocalDate from,LocalDate to) {
        var pageable=com.railway.reservationservice.config.Paging.of(page,size,sort,direction,Set.of("bookingTime","dateOfJourney","trainNumber","status","pnr"),"reservationId");
        if(from!=null && to!=null && from.isAfter(to)) throw BookingException.badRequest("fromDate must not follow toDate");
        Reservation.ReservationStatus wanted=null;
        if(status!=null && !status.isBlank()) {
            try { wanted=Reservation.ReservationStatus.valueOf(switch(status.toUpperCase(Locale.ROOT)){case "CNF"->"CONFIRMED";case "WL"->"WAITING";default->status.toUpperCase(Locale.ROOT);}); }
            catch(IllegalArgumentException ex){throw BookingException.badRequest("Unknown booking status");}
        }
        // Refresh lifecycle before filtering so expired holds never remain in a pending-status page.
        reservations.userMaintenanceKeys(username).forEach(key->{lockInventory(key);maintain(key);});
        final var filterStatus=wanted;
        return PageResult.of(reservations.findAll((org.springframework.data.jpa.domain.Specification<Reservation>)(r,q,b)->{
            var conditions=new ArrayList<jakarta.persistence.criteria.Predicate>(); conditions.add(b.equal(r.get("username"),username));
            if(filterStatus!=null) conditions.add(b.equal(r.get("status"),filterStatus));
            if(from!=null) conditions.add(b.greaterThanOrEqualTo(r.get("dateOfJourney"),from));
            if(to!=null) conditions.add(b.lessThanOrEqualTo(r.get("dateOfJourney"),to));
            return b.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        },pageable).map(this::map));
    }
    private BigDecimal fee(Reservation r) {
        if(cancellationRate.signum()<0 || cancellationRate.compareTo(BigDecimal.ONE)>0) throw new IllegalStateException("Invalid cancellation rate");
        return r.getRazorpayPaymentId()!=null && r.getDataVersion()>0 && r.getStatus()==CONFIRMED
            ? money(r.getTotalFare()).multiply(cancellationRate).setScale(2,RoundingMode.HALF_UP):BigDecimal.ZERO;
    }
    private void cancellable(Reservation r) {
        if(r.getStatus()==EXPIRED) throw BookingException.conflict("This unpaid booking has expired");
        if(r.getDepartureAt()!=null && !r.getDepartureAt().isAfter(clock.instant())) throw BookingException.conflict("Cancellation closes at boarding time");
        if(r.getDataVersion()==0 && !r.getDateOfJourney().isAfter(LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))))) throw BookingException.conflict("Legacy booking cancellation requires administrator review");
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public CancellationResponse preview(String pnr,String username,boolean admin) {
        Reservation r=locked(pnr); authorize(r,username,admin); maintain(r.getInventoryKey());
        if(r.getStatus()==CANCELLED) return cancellation(r);
        cancellable(r); BigDecimal charge=fee(r);
        return CancellationResponse.builder().pnr(pnr).message("Preview only; the current booking status determines the fee at cancellation.")
            .originalFare(money(r.getTotalFare())).cancellationFee(charge).refundAmount(r.getRazorpayPaymentId()!=null?money(r.getTotalFare()).subtract(charge):BigDecimal.ZERO)
            .refundStatus("NOT_REQUESTED").currentStatus(r.getStatus().name()).testMode(true).build();
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public CancellationResponse cancel(String pnr,String username,boolean admin) {
        Reservation r=locked(pnr); authorize(r,username,admin); maintain(r.getInventoryKey());
        if(r.getStatus()==CANCELLED) return cancellation(r);
        cancellable(r); cancelPaid(r,fee(r),"CANCELLED","Your whole booking was cancelled."); maintain(r.getInventoryKey()); return cancellation(r);
    }
    private void cancelPaid(Reservation r,BigDecimal fee,String type,String message) {
        boolean paid=r.getRazorpayPaymentId()!=null && r.getDataVersion()>0;
        r.setCancellationFee(fee); r.setRefundAmount(paid?money(r.getTotalFare()).subtract(fee):BigDecimal.ZERO);
        r.setRefundStatus(paid && r.getRefundAmount().signum()>0?"PENDING":"NONE"); setStatus(r,CANCELLED); event(r,type,message);
        if("PENDING".equals(r.getRefundStatus())) event(r,"REFUND_INITIATED","Test refund initiated: INR "+r.getRefundAmount()+".");
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public PaymentContext paymentContext(String pnr) {
        Reservation r=locked(pnr); maintain(r.getInventoryKey());
        return new PaymentContext(r.getPnr(),r.getUsername(),r.getTotalFare(),r.getCurrency(),r.getDataVersion()==0?"LEGACY":r.getStatus().name(),r.getHoldExpiresAt(),r.getRazorpayPaymentId(),money(r.getRefundAmount()),r.getRefundStatus());
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public PaymentDecision confirm(String pnr,PaymentConfirmation payment) {
        Reservation r=locked(pnr); maintain(r.getInventoryKey());
        if(r.getDataVersion()==0 || r.getTotalFare()==null) throw BookingException.conflict("Legacy bookings cannot accept payments");
        if(!"INR".equals(payment.currency()) || payment.amount().compareTo(r.getTotalFare())!=0) throw BookingException.conflict("Captured payment does not match saved fare");
        if(r.getRazorpayPaymentId()!=null && !r.getRazorpayPaymentId().equals(payment.razorpayPaymentId())) return new PaymentDecision(pnr,r.getStatus().name(),true,payment.amount());
        boolean previouslyPaid=r.getRazorpayPaymentId()!=null;
        r.setRazorpayOrderId(payment.razorpayOrderId()); r.setRazorpayPaymentId(payment.razorpayPaymentId());
        if(r.getStatus()==EXPIRED || r.getStatus()==CANCELLED) {
            if(!previouslyPaid){r.setRefundAmount(payment.amount()); r.setCancellationFee(BigDecimal.ZERO); r.setRefundStatus("PENDING"); event(r,"REFUND_INITIATED","Late test payment received. Full test refund initiated: INR "+payment.amount()+".");}
            return new PaymentDecision(pnr,r.getStatus().name(),true,money(r.getRefundAmount()));
        }
        if(r.getStatus()==PENDING_PAYMENT) {
            setStatus(r,r.isWaitlistRequested()?WAITING:CONFIRMED);
            event(r,"PAYMENT_RECEIVED","Test payment received: INR "+r.getTotalFare()+". Booking status: "+(r.isWaitlistRequested()?"WL":"CNF")+
                (r.isWaitlistRequested()?". Confirmation depends on availability before "+r.getWaitlistDeadline():". Seats: "+seatSummary(r)));
            reservations.flush(); maintain(r.getInventoryKey());
        }
        return new PaymentDecision(pnr,r.getStatus().name(),false,BigDecimal.ZERO);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void paymentFailed(String pnr) {
        Reservation r=locked(pnr); maintain(r.getInventoryKey());
        // A delayed failed attempt must not contradict a successful payment or reopen an expired hold.
        if(r.getStatus()==PENDING_PAYMENT && r.getRazorpayPaymentId()==null)
            event(r,"PAYMENT_FAILED","The payment provider reported a failed attempt. Retry only while your booking hold remains active.");
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void refundStatus(String pnr,RefundUpdate update) {
        Reservation r=locked(pnr);
        if(!Objects.equals(r.getRazorpayPaymentId(),update.razorpayPaymentId())) return;
        if(update.refundAmount().compareTo(money(r.getRefundAmount()))!=0) throw BookingException.conflict("Refund amount does not match booking");
        if("PROCESSED".equals(r.getRefundStatus())) return;
        r.setRefundStatus(Set.of("PROCESSED","REFUNDED","COMPLETED").contains(update.refundStatus())?"PROCESSED":update.refundStatus());
        if("PROCESSED".equals(r.getRefundStatus())) event(r,"REFUND_COMPLETED","Test refund completed: INR "+r.getRefundAmount()+".");
        else if("FAILED".equals(r.getRefundStatus())) event(r,"REFUND_DELAYED","Your test refund needs attention. Check your booking for updates.");
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void expireHolds(){for(String key:reservations.maintenanceKeys()){lockInventory(key); maintain(key);}}
    private void authorize(Reservation r,String username,boolean admin){if(!admin && !r.getUsername().equals(username)) throw new BookingException(HttpStatus.FORBIDDEN,"You cannot access this booking");}
    private static BigDecimal money(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
    private CancellationResponse cancellation(Reservation r){return CancellationResponse.builder().pnr(r.getPnr()).message("Booking cancelled. Any applicable refund is a test-mode refund.")
        .originalFare(money(r.getTotalFare())).cancellationFee(money(r.getCancellationFee())).refundAmount(money(r.getRefundAmount())).refundStatus(r.getRefundStatus()).currentStatus(r.getStatus().name()).testMode(true).build();}
    public ReservationRes map(Reservation r) {
        Integer position=null;
        if(r.getStatus()==WAITING && r.getInventoryKey()!=null){List<Reservation> queue=waiting(r.getInventoryKey()); for(int i=0;i<queue.size();i++) if(queue.get(i).getPnr().equals(r.getPnr())){position=i+1; break;}}
        return ReservationRes.builder().operatorCancellationReason(r.getOperatorCancellationReason()).operatorCancelledAt(r.getOperatorCancelledAt()).trainName(r.getTrainName()).cancellationFee(money(r.getCancellationFee())).pnr(r.getPnr()).trainNumber(r.getTrainNumber()).dateOfJourney(r.getDateOfJourney()).bookingTime(r.getBookingTime()).status(r.getStatus().name())
            .fromStation(r.getFromStation()).toStation(r.getToStation()).seatClass(r.getSeatClass()).runDate(r.getRunDate()).departureAt(r.getDepartureAt()).holdExpiresAt(r.getHoldExpiresAt())
            .waitlistDeadline(r.getWaitlistDeadline()).waitlistPosition(position)
            .allocation(r.getStatus()==WAITING || (r.getStatus()==PENDING_PAYMENT && r.isWaitlistRequested())?"WL":r.getStatus()==CONFIRMED || r.getStatus()==PENDING_PAYMENT?"CNF":null)
            .farePerPassenger(r.getFarePerPassenger()).totalFare(r.getTotalFare()).currency(r.getCurrency()).baseFare(r.getBaseFare()).ratePerKm(r.getRatePerKm()).minimumFare(r.getMinimumFare()).distanceKm(r.getDistanceKm())
            .paymentStatus(r.getRazorpayPaymentId()!=null?"CAPTURED":"UNPAID").refundStatus(r.getRefundStatus()).refundAmount(money(r.getRefundAmount())).legacyBooking(r.getDataVersion()==0).testMode(true)
            .passengers(r.getPassengers().stream().map(p->PassengerDto.builder().name(p.getName()).age(p.getAge()).gender(p.getGender()).birthPreference(p.getBirthPreference()).seatNumber(p.getSeatNumber()).seatDetails(SeatLayouts.describe(r.getSeatClass(),p.getSeatNumber())).bookingStatus(p.getBookingStatus().name()).build()).toList()).build();
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public RunCancellationView cancelRun(String train,LocalDate date,Instant departure,String reason,String admin) {
        String key=InventoryInitializer.runKey(train,date);
        lockRun(key);
        var existing=cancelledRuns.findById(key);
        if(existing.isPresent()) return runView(existing.get(),true);
        if(!departure.isAfter(clock.instant())) throw BookingException.conflict("Only runs that have not departed from their first station can be cancelled");
        // Legacy rows cannot reliably identify an original run or its captured fare.
        if(reservations.existsByTrainNumberAndRunDateIsNullAndStatusIn(train,List.of(CONFIRMED,WAITING,PENDING_PAYMENT)))
            throw BookingException.conflict("This train has legacy active bookings without a run date; review those records before cancelling a run");
        var decision=new TrainRunCancellation(); decision.setRunKey(key); decision.setTrainNumber(train);
        decision.setRunDate(date); decision.setSourceDepartureAt(departure); decision.setReason(reason);
        decision.setCancelledBy(admin); decision.setCancelledAt(clock.instant()); cancelledRuns.saveAndFlush(decision);
        var affected=reservations.findByTrainNumberAndRunDateOrderByReservationIdAsc(train,date);
        for(var r:affected) {
            if(!List.of(CONFIRMED,WAITING,PENDING_PAYMENT).contains(r.getStatus())) continue;
            if(r.getDataVersion()<2 || r.getInventoryKey()==null || r.getTotalFare()==null)
                throw BookingException.conflict("An affected booking requires legacy data review; cancellation was not applied");
            lockInventory(r.getInventoryKey());
            r.setOperatorPreviousStatus(r.getStatus().name()); r.setOperatorCancellationReason(reason); r.setOperatorCancelledAt(decision.getCancelledAt());
            cancelPaid(r,BigDecimal.ZERO,"TRAIN_CANCELLED","The operator cancelled train "+train+" starting on "+date+". Reason: "+reason+". Any captured ticket fare is refunded in full, without deduction. Review separately booked onward journeys and remove any imported calendar reminder.");
        }
        reservations.flush();
        return runView(decision,true);
    }
    @Transactional(readOnly=true)
    public RunCancellationView previewRun(String train,LocalDate date,Instant departure) {
        var existing=cancelledRuns.findById(InventoryInitializer.runKey(train,date));
        if(existing.isPresent()) return runView(existing.get(),true);
        var draft=new TrainRunCancellation(); draft.setTrainNumber(train); draft.setRunDate(date); draft.setSourceDepartureAt(departure);
        return runView(draft,false);
    }
    private RunCancellationView runView(TrainRunCancellation run,boolean cancelled) {
        var rows=reservations.findByTrainNumberAndRunDateOrderByReservationIdAsc(run.getTrainNumber(),run.getRunDate()).stream()
            .filter(r->cancelled?r.getOperatorCancelledAt()!=null:List.of(CONFIRMED,WAITING,PENDING_PAYMENT).contains(r.getStatus())).toList();
        BigDecimal total=BigDecimal.ZERO,processed=BigDecimal.ZERO,outstanding=BigDecimal.ZERO;
        for(var r:rows) if(r.getRazorpayPaymentId()!=null) {
            BigDecimal amount=cancelled?money(r.getRefundAmount()):money(r.getTotalFare()); total=total.add(amount);
            if(cancelled && "PROCESSED".equals(r.getRefundStatus())) processed=processed.add(amount); else outstanding=outstanding.add(amount);
        }
        return new RunCancellationView(run.getTrainNumber(),run.getRunDate(),run.getSourceDepartureAt(),cancelled,run.getReason(),run.getCancelledBy(),run.getCancelledAt(),
            rows.size(),rows.stream().mapToLong(r->r.getPassengers().size()).sum(),
            rows.stream().filter(r->"CONFIRMED".equals(cancelled?r.getOperatorPreviousStatus():r.getStatus().name())).count(),
            rows.stream().filter(r->"WAITING".equals(cancelled?r.getOperatorPreviousStatus():r.getStatus().name())).count(),
            rows.stream().filter(r->r.getRazorpayPaymentId()==null).count(),total,processed,outstanding);
    }
}
