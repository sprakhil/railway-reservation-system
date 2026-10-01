package com.railway.reservationservice.service;

import com.railway.reservationservice.entity.Reservation;
import com.railway.reservationservice.repository.ReservationRepository;
import com.railway.reservationservice.dto.PageResult;
import com.railway.reservationservice.exception.BookingException;
import com.railway.reservationservice.config.Paging;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AdminBookingService {
    private final ReservationRepository bookings;
    @PersistenceContext private EntityManager em;
    public record Filter(String search,String username,String trainNumber,String status,LocalDate fromDate,LocalDate toDate){}
    public record Row(String pnr,String username,String email,String trainNumber,String fromStation,String toStation,LocalDate dateOfJourney,LocalDateTime bookingTime,String status,BigDecimal totalFare,boolean paid,BigDecimal cancellationFee,BigDecimal refundAmount,String refundStatus){}
    public record Bucket(String label,long bookings,long cancellations,long paidBookings,BigDecimal gross,BigDecimal refunded,BigDecimal outstandingRefunds,BigDecimal net){
        public double cancellationRate(){return bookings==0?0:BigDecimal.valueOf(cancellations*100.0/bookings).setScale(2,RoundingMode.HALF_UP).doubleValue();}
    }
    public record Dashboard(Bucket totals,Map<String,Long> byStatus,List<Bucket> popularTrains,Instant generatedAt){}
    public record Report(LocalDate fromDate,LocalDate toDate,String groupBy,String dateBasis,Bucket totals,List<Bucket> rows,Instant generatedAt){}
    private Specification<Reservation> filter(Filter f){
        if(f.fromDate()!=null&&f.toDate()!=null&&f.fromDate().isAfter(f.toDate()))throw BookingException.badRequest("From date must not follow to date");
        String search=clean(f.search()),username=clean(f.username()),train=clean(f.trainNumber());
        var wanted=clean(f.status()).isEmpty()?null:status(f.status());
        String like="%"+search.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        return (r,q,b)->{
            var p=new ArrayList<Predicate>();
            if(!search.isEmpty())p.add(b.or(b.like(b.lower(r.get("pnr")),like,'!'),b.like(b.lower(r.get("username")),like,'!'),b.like(b.lower(r.get("userEmail")),like,'!')));
            if(!username.isEmpty())p.add(b.equal(r.get("username"),username));
            if(!train.isEmpty())p.add(b.equal(r.get("trainNumber"),train.toUpperCase(Locale.ROOT)));
            if(wanted!=null)p.add(b.equal(r.get("status"),wanted));
            if(f.fromDate()!=null)p.add(b.greaterThanOrEqualTo(r.get("dateOfJourney"),f.fromDate()));
            if(f.toDate()!=null)p.add(b.lessThanOrEqualTo(r.get("dateOfJourney"),f.toDate()));
            return b.and(p.toArray(Predicate[]::new));
        };
    }
    private String clean(String s){if(s==null)return "";if(s.length()>100)throw BookingException.badRequest("Filter exceeds 100 characters");return s.strip();}
    private Reservation.ReservationStatus status(String s){try{return Reservation.ReservationStatus.valueOf(s.strip().toUpperCase(Locale.ROOT));}catch(Exception ex){throw BookingException.badRequest("Unknown booking status");}}
    private boolean paid(Reservation r){return r.getDataVersion()>0&&r.getRazorpayPaymentId()!=null;}
    private static BigDecimal money(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
    private Row row(Reservation r){return new Row(r.getPnr(),r.getUsername(),r.getUserEmail(),r.getTrainNumber(),r.getFromStation(),r.getToStation(),r.getDateOfJourney(),r.getBookingTime(),r.getStatus().name(),money(r.getTotalFare()),paid(r),money(r.getCancellationFee()),money(r.getRefundAmount()),r.getRefundStatus());}
    public PageResult<Row> list(Filter f,int page,int size,String sort,String direction){
        var p=Paging.of(page,size,sort,direction,Set.of("bookingTime","dateOfJourney","trainNumber","status","pnr","username","totalFare"),"reservationId");
        return PageResult.of(bookings.findAll(filter(f),p).map(this::row));
    }
    private List<Bucket> aggregate(Filter f,String group,int limit){
        var b=em.getCriteriaBuilder();var q=b.createTupleQuery();var r=q.from(Reservation.class);
        Predicate paid=b.and(b.greaterThan(r.get("dataVersion"),0),b.isNotNull(r.get("razorpayPaymentId")));
        Predicate cancelled=b.equal(r.get("status"),Reservation.ReservationStatus.CANCELLED);
        Expression<Long> nPaid=b.sum(b.<Long>selectCase().when(paid,1L).otherwise(0L));
        Expression<BigDecimal> gross=b.sum(b.<BigDecimal>selectCase().when(paid,b.coalesce(r.<BigDecimal>get("totalFare"),BigDecimal.ZERO)).otherwise(BigDecimal.ZERO));
        Expression<BigDecimal> refunded=b.sum(b.<BigDecimal>selectCase().when(b.and(paid,b.equal(r.get("refundStatus"),"PROCESSED")),b.coalesce(r.<BigDecimal>get("refundAmount"),BigDecimal.ZERO)).otherwise(BigDecimal.ZERO));
        Predicate due=b.and(paid,b.greaterThan(r.get("refundAmount"),BigDecimal.ZERO),b.or(b.isNull(r.get("refundStatus")),b.notEqual(r.get("refundStatus"),"PROCESSED")));
        Expression<BigDecimal> pending=b.sum(b.<BigDecimal>selectCase().when(due,r.<BigDecimal>get("refundAmount")).otherwise(BigDecimal.ZERO));
        Expression<?> key=group.equals("train")?r.get("trainNumber"):group.equals("day")?r.get("dateOfJourney"):b.literal("All bookings");
        q.multiselect(key,b.count(r),b.sum(b.<Long>selectCase().when(cancelled,1L).otherwise(0L)),nPaid,gross,refunded,pending).where(filter(f).toPredicate(r,q,b));
        if(!group.equals("all"))q.groupBy(key);
        if(group.equals("train"))q.orderBy(b.desc(nPaid),b.asc(key));else if(group.equals("day"))q.orderBy(b.asc(key));
        var result=em.createQuery(q).setMaxResults(limit).getResultList();
        return result.stream().map(t->{var g=amount(t,4);var done=amount(t,5);var outstanding=amount(t,6);return new Bucket(String.valueOf(t.get(0)),number(t,1),number(t,2),number(t,3),g,done,outstanding,g.subtract(done).subtract(outstanding));}).toList();
    }
    private long number(Tuple t,int index){return t.get(index)==null?0:((Number)t.get(index)).longValue();}
    private BigDecimal amount(Tuple t,int index){return money((BigDecimal)t.get(index));}
    public Dashboard dashboard(){
        var f=new Filter(null,null,null,null,null,null);var b=em.getCriteriaBuilder();var q=b.createTupleQuery();var r=q.from(Reservation.class);
        q.multiselect(r.get("status"),b.count(r)).groupBy(r.get("status"));var statuses=new LinkedHashMap<String,Long>();
        for(var s:Reservation.ReservationStatus.values())statuses.put(s.name(),0L);
        for(var tuple:em.createQuery(q).getResultList())if(tuple.get(0)!=null)statuses.put(tuple.get(0).toString(),number(tuple,1));
        return new Dashboard(aggregate(f,"all",1).getFirst(),statuses,aggregate(f,"train",10).stream().filter(t->t.paidBookings()>0).toList(),Instant.now());
    }
    public Report report(LocalDate from,LocalDate to,String train,String group){
        if(from==null||to==null||from.isAfter(to)||ChronoUnit.DAYS.between(from,to)>365)throw BookingException.badRequest("Choose an inclusive journey-date range of at most 366 days");
        if(!Set.of("day","month","train").contains(group))throw BookingException.badRequest("Group by day, month or train");
        var f=new Filter(null,null,train,null,from,to);var rows=aggregate(f,group.equals("month")?"day":group,10001);
        if(rows.size()>10000)throw BookingException.conflict("Report is too large; narrow the filters");
        if(group.equals("month")){
            var months=new TreeMap<String,Bucket>();
            for(var v:rows){String key=v.label().substring(0,7);var old=months.getOrDefault(key,new Bucket(key,0,0,0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));months.put(key,new Bucket(key,old.bookings()+v.bookings(),old.cancellations()+v.cancellations(),old.paidBookings()+v.paidBookings(),old.gross().add(v.gross()),old.refunded().add(v.refunded()),old.outstandingRefunds().add(v.outstandingRefunds()),old.net().add(v.net())));}rows=new ArrayList<>(months.values());
        }
        return new Report(from,to,group,"JOURNEY_DATE",aggregate(f,"all",1).getFirst(),rows,Instant.now());
    }
    public String export(Filter f,boolean cancellations){
        if(cancellations)f=new Filter(f.search(),f.username(),f.trainNumber(),"CANCELLED",f.fromDate(),f.toDate());
        var page=bookings.findAll(filter(f),PageRequest.of(0,10001,Sort.by("reservationId")));
        if(page.getTotalElements()>10000)throw BookingException.conflict("Export exceeds 10000 bookings; narrow the filters");
        var csv=new StringBuilder("\uFEFFPNR,Account key,Email,Train,From,To,Journey date,Booked at UTC,Status,Quoted fare INR,Verified payment,Cancellation fee INR,Refund amount INR,Refund status\r\n");
        for(var r:page){var v=row(r);append(csv,v.pnr(),v.username(),v.email(),v.trainNumber(),v.fromStation(),v.toStation(),v.dateOfJourney(),v.bookingTime(),v.status(),v.totalFare(),v.paid(),v.cancellationFee(),v.refundAmount(),v.refundStatus());}return csv.toString();
    }
    public String reportCsv(LocalDate from,LocalDate to,String train,String group){
        var report=report(from,to,train,group);var csv=new StringBuilder("\uFEFFJourney date from,Journey date to,Group,Bookings,Cancellations,Cancellation rate percent,Paid bookings,Captured booking fares INR,Processed refunds INR,Outstanding refunds INR,Net after all refunds INR\r\n");
        for(var v:report.rows())append(csv,from,to,v.label(),v.bookings(),v.cancellations(),v.cancellationRate(),v.paidBookings(),v.gross(),v.refunded(),v.outstandingRefunds(),v.net());return csv.toString();
    }
    private static void append(StringBuilder out,Object... values){out.append(String.join(",",Arrays.stream(values).map(v->cell(v==null?"":v.toString())).toList())).append("\r\n");}
    static String cell(String s){if(s.stripLeading().matches("(?s)^[=+@-].*")||s.startsWith("\t")||s.startsWith("\r")||s.startsWith("\n"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
}
