package com.railway.reservationservice.service;

import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.dto.SupportTypes.*;
import com.railway.reservationservice.entity.*;
import com.railway.reservationservice.entity.SupportTicket.*;
import com.railway.reservationservice.exception.BookingException;
import com.railway.reservationservice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @RequiredArgsConstructor
public class SupportService {
    private final SupportTicketRepository tickets;
    private final SupportMessageRepository messages;
    private final ReservationRepository bookings;
    private final BookingNotificationRepository outbox;
    private final NotificationCenter notifications;
    private final Clock clock;
    @Value("${support.email:}") private String supportEmail="";

    @Transactional
    public Ticket create(String owner,String email,Create input) {
        if(email==null || email.isBlank()) throw BookingException.badRequest("A verified account email is required");
        String subject=clean(input.subject(),160),body=clean(input.message(),3000);
        if(input.category()==null) throw BookingException.badRequest("Choose a support category");
        String id="SUP-"+UUID.nameUUIDFromBytes((owner+":"+uuid(input.requestId())).getBytes(StandardCharsets.UTF_8));
        String hash=hash(new tools.jackson.databind.json.JsonMapper().writeValueAsString(List.of(input.category().name(),subject,Objects.toString(input.pnr(),""),body)));
        var previous=tickets.findById(id);
        if(previous.isPresent()) {
            if(!previous.get().getRequestHash().equals(hash)) throw BookingException.conflict("This submission reference was used for a different request");
            return view(previous.get(),false);
        }
        if(input.pnr()!=null) {
            if(!input.pnr().matches("[0-9]{10}")) throw BookingException.badRequest("PNR must contain 10 digits");
            var booking=bookings.findByPnr(input.pnr());
            if(booking.isEmpty() || !booking.get().getUsername().equals(owner)) throw new BookingException(HttpStatus.NOT_FOUND,"Booking not found in your account");
        }
        var t=new SupportTicket();t.setId(id);t.setUsername(owner);t.setUserEmail(email);t.setSubject(subject);
        t.setCategory(input.category());t.setPnr(input.pnr());t.setStatus(Status.OPEN);t.setCreatedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));t.setUpdatedAt(t.getCreatedAt());t.setRequestHash(hash);
        tickets.saveAndFlush(t);
        var m=append(t,"initial",owner,false,false,body);
        notify(t,m,"SUPPORT_CREATED","Your support request has been received",true);
        return view(t,false);
    }

    @Transactional(readOnly=true)
    public PageResult<Ticket> list(String owner,boolean admin,int page,int size,String status,String category,String search) {
        paging(page,size);
        Status selectedStatus=parse(Status.class,status);Category selectedCategory=parse(Category.class,category);
        String term=search==null?"":search.strip().toLowerCase(Locale.ROOT);
        if(term.length()>160) throw BookingException.badRequest("Search must be at most 160 characters");
        String like="%"+term.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        var result=tickets.findAll((root,query,cb)->{
            var clauses=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if(!admin) clauses.add(cb.equal(root.get("username"),owner));
            if(selectedStatus!=null)clauses.add(cb.equal(root.get("status"),selectedStatus));
            if(selectedCategory!=null)clauses.add(cb.equal(root.get("category"),selectedCategory));
            if(!term.isEmpty()) {
                var fields=new ArrayList<jakarta.persistence.criteria.Predicate>();
                for(String field:admin?List.of("id","subject","pnr","username","userEmail"):List.of("id","subject","pnr"))
                    fields.add(cb.like(cb.lower(root.get(field)),like,'\\'));
                clauses.add(cb.or(fields.toArray(jakarta.persistence.criteria.Predicate[]::new)));
            }
            return cb.and(clauses.toArray(jakarta.persistence.criteria.Predicate[]::new));
        },PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"updatedAt","id")));
        return PageResult.of(result.map(t->view(t,admin)));
    }
    @Transactional(readOnly=true) public Ticket get(String id,String owner,boolean admin){return view(access(id,owner,admin,false),admin);}
    @Transactional(readOnly=true) public PageResult<Message> conversation(String id,String owner,boolean admin,int page,int size) {
        paging(page,size);access(id,owner,admin,false);
        return PageResult.of(messages.findByTicketId(id,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt","id")))
            .map(m->new Message(m.getId(),m.isStaff()&&!admin?"Railwize support":m.getAuthor(),m.isStaff(),m.isSystemMessage(),m.getBody(),m.getCreatedAt())));
    }
    @Transactional
    public Ticket reply(String id,String owner,boolean admin,Reply input) {
        var t=access(id,owner,admin,true);String body=clean(input.message(),3000),key=id+":"+uuid(input.requestId());
        var previous=messages.findById(key);
        if(previous.isPresent()) {
            var p=previous.get();if(!p.getAuthor().equals(owner)||p.isStaff()!=admin||!p.getBody().equals(body)) throw BookingException.conflict("This reply reference was already used");
            return view(t,admin);
        }
        t.setStatus(admin?Status.IN_PROGRESS:Status.OPEN);touch(t);
        var m=append(t,uuid(input.requestId()),owner,admin,false,body);
        tickets.saveAndFlush(t);
        if(admin)notify(t,m,"SUPPORT_REPLY","Railwize support replied to your request",false);
        else mail(t,m,"SUPPORT_USER_REPLY","A passenger replied to a support request",supportEmail,"admin");
        return view(t,admin);
    }
    @Transactional
    public Ticket status(String id,String actor,ChangeStatus input) {
        var t=access(id,actor,true,true);
        if(input.status()==null || input.version()==null)throw BookingException.badRequest("Status and version are required");
        if(t.getVersion()!=input.version())throw BookingException.conflict("This request changed. Refresh before updating its status");
        if(t.getStatus()==input.status())return view(t,true);
        t.setStatus(input.status());touch(t);
        var m=append(t,UUID.randomUUID().toString(),actor,true,true,"Status changed to "+input.status().name().replace('_',' ').toLowerCase(Locale.ROOT)+".");
        tickets.saveAndFlush(t);notify(t,m,"SUPPORT_STATUS","Your support request is "+input.status().name().replace('_',' ').toLowerCase(Locale.ROOT),false);
        return view(t,true);
    }
    private SupportTicket access(String id,String owner,boolean admin,boolean lock) {
        if(id==null||!id.matches("SUP-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw missing();
        var t=(lock?tickets.lock(id):tickets.findById(id)).orElseThrow(SupportService::missing);
        if(!admin&&!t.getUsername().equals(owner))throw missing();return t;
    }
    private SupportMessage append(SupportTicket t,String key,String author,boolean staff,boolean system,String body) {
        var m=new SupportMessage();m.setId(t.getId()+":"+key);m.setTicketId(t.getId());m.setAuthor(author);m.setStaff(staff);m.setSystemMessage(system);m.setBody(body);m.setCreatedAt(t.getUpdatedAt());return messages.save(m);
    }
    private void touch(SupportTicket t) {
        // Match DATETIME(6) precision so rapid replies remain ordered after a database round trip.
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var previous = t.getUpdatedAt().truncatedTo(ChronoUnit.MICROS);
        t.setUpdatedAt(now.isAfter(previous) ? now : previous.plus(1, ChronoUnit.MICROS));
    }
    private void notify(SupportTicket t,SupportMessage m,String type,String title,boolean alertAdmin) {
        String eventId=m.getId()+":user";
        notifications.support(eventId,t.getUsername(),t.getId(),t.getPnr(),type,title,t.getSubject());
        mail(t,m,type,title,t.getUserEmail(),"user");
        if(alertAdmin)mail(t,m,"SUPPORT_NEW","A new support request needs attention",supportEmail,"admin");
    }
    private void mail(SupportTicket t,SupportMessage m,String type,String title,String recipient,String audience) {
        if(recipient==null||recipient.isBlank())return;
        var e=new BookingNotification();e.setEventId(m.getId()+":"+audience);e.setSupportTicketId(t.getId());e.setPnr(t.getPnr());e.setEventType(type);
        // Conversation bodies stay behind authentication instead of being copied into mailbox previews.
        e.setMessage(title+". Request: "+t.getSubject()+". Sign in to read the conversation and reply.");
        e.setUserEmail(recipient.strip());e.setUsername(t.getUsername());e.setNextAttemptAt(clock.instant());outbox.save(e);
    }
    private Ticket view(SupportTicket t,boolean admin){return new Ticket(t.getId(),t.getSubject(),t.getCategory(),t.getStatus(),t.getPnr(),t.getUsername(),admin?t.getUserEmail():null,t.getCreatedAt(),t.getUpdatedAt(),t.getVersion());}
    private static String clean(String text,int max){if(text==null||text.isBlank()||text.strip().length()>max)throw BookingException.badRequest("Text is required and must be at most "+max+" characters");return text.strip();}
    private static String uuid(String value){try{var id=UUID.fromString(value);if(!id.toString().equalsIgnoreCase(value))throw new IllegalArgumentException();return id.toString();}catch(Exception ex){throw BookingException.badRequest("Use a UUID submission reference");}}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
    private static void paging(int page,int size){if(page<0||size<1||size>50)throw BookingException.badRequest("Page must be non-negative and size must be 1-50");}
    private static <E extends Enum<E>> E parse(Class<E> type,String value){if(value==null||value.isBlank())return null;try{return Enum.valueOf(type,value);}catch(IllegalArgumentException ex){throw BookingException.badRequest("Choose a valid "+type.getSimpleName().toLowerCase(Locale.ROOT));}}
    private static BookingException missing(){return new BookingException(HttpStatus.NOT_FOUND,"Support request not found");}
}
