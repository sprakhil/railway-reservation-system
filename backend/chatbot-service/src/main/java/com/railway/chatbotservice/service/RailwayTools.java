package com.railway.chatbotservice.service;
import com.railway.chatbotservice.api.ChatTypes.Card;
import com.railway.chatbotservice.config.SecurityConfig.Identity;
import org.springframework.ai.tool.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Only these explicitly registered read-only methods are callable by the model. */
public class RailwayTools {
    private final WebClient reservation,train;
    private final Identity identity;private final Conversations.State conversation;
    private final AtomicInteger calls=new AtomicInteger();
    private final List<Card> cards=new CopyOnWriteArrayList<>();
    public RailwayTools(WebClient reservation,WebClient train,Identity identity,Conversations.State conversation){this.reservation=reservation;this.train=train;this.identity=identity;this.conversation=conversation;}
    public List<Card> cards(){return List.copyOf(cards);}
    public boolean used(){return calls.get()>0;}
    @Tool(description="Read ONLY the signed-in account's bookings without asking for a PNR. scope: ALL for history, CANCELLED, CONFIRMED, WAITING, PENDING_PAYMENT, EXPIRED; COUNT for exact counts by status; NEXT for the earliest future paid confirmed or waitlisted journey. page is zero-based, 8 bookings per page. Never accept another username. Private details are shown only in local cards. Return the notice accurately, including empty results and partial lists.")
    public Map<String,Object> myBookings(String scope,int page){
        String selected=scope==null?"ALL":scope.toUpperCase(Locale.ROOT);
        if(!Set.of("ALL","CANCELLED","CONFIRMED","WAITING","PENDING_PAYMENT","EXPIRED","COUNT","NEXT").contains(selected)||page<0||page>10000)
            return Map.of("notice","Choose a supported booking filter and page.");
        try{
            cards.removeIf(c->c.kind().equals("BOOKING"));
            if(selected.equals("COUNT")||selected.equals("NEXT")){
                var data=get(reservation,"/api/reservations/my-bookings/overview",Map.of());
                if(selected.equals("COUNT")){
                    var counts=data.path("byStatus");
                    String notice="You have "+data.path("totalBookings").asLong()+" booking records (PNRs), including "+
                        counts.path("CONFIRMED").asLong()+" confirmed, "+counts.path("WAITING").asLong()+" waitlisted, "+
                        counts.path("CANCELLED").asLong()+" cancelled, "+counts.path("PENDING_PAYMENT").asLong()+" awaiting payment and "+
                        counts.path("EXPIRED").asLong()+" expired. These are bookings, not passenger counts.";
                    return Map.of("notice",notice);
                }
                var next=data.path("nextJourney");
                if(next.isMissingNode()||next.isNull())return Map.of("notice","You have no upcoming confirmed or waitlisted journey. Unpaid, cancelled, expired and departed bookings are excluded.");
                cards.add(bookingCard(next));
                return Map.of("notice","Your next scheduled journey is shown in the booking card, with departure time in India time. "+
                    (text(next,"status").matches("WAITING|WL")?"It is waitlisted; travel is not confirmed.":"It is confirmed.")+" Unpaid, cancelled, expired and departed bookings are excluded.");
            }
            var params=new HashMap<String,String>(Map.of("page",String.valueOf(page),"size","8","sort","bookingTime","direction","desc"));
            if(!selected.equals("ALL"))params.put("status",selected);
            var data=get(reservation,"/api/reservations/my-bookings",params);
            for(var row:data.path("content"))if(cards.size()<8)cards.add(bookingCard(row));
            long total=data.path("totalElements").asLong();int pages=data.path("totalPages").asInt();
            String label=selected.equals("ALL")?"booking records (all statuses)":selected.toLowerCase(Locale.ROOT).replace('_',' ')+" bookings";
            String notice=total==0?"You have no "+label+".":"You have "+total+" "+label+". Showing "+cards.size()+" on page "+(page+1)+" of "+pages+". Details are in the booking cards.";
            if(page+1<pages)notice+=" Ask for my "+(selected.equals("ALL")?"":selected.toLowerCase(Locale.ROOT)+" ")+"bookings page "+(page+2)+" to see more.";
            if(page>=pages&&total>0)notice="That page is empty. You have "+pages+" pages of "+label+"; ask for page 1.";
            return Map.of("notice",notice,"totalBookings",total,"page",page,"totalPages",pages);
        }catch(Exception unavailable){cards.removeIf(c->c.kind().equals("BOOKING"));return Map.of("notice","I could not retrieve your bookings right now. Please retry or open My journeys. This does not mean you have no bookings.");}
    }
    private Card bookingCard(JsonNode n){
        return new Card("BOOKING",text(n,"trainName").isBlank()?"Your booking":text(n,"trainName"),text(n,"pnr"),text(n,"trainNumber"),text(n,"fromStation"),text(n,"toStation"),text(n,"dateOfJourney"),text(n,"seatClass"),n.path("passengers").size(),text(n,"status"),n.path("totalFare").asDouble(),null,text(n,"departureAt"),text(n,"refundStatus"),n.path("refundAmount").asDouble());
    }
    private void limit(){if(calls.incrementAndGet()>6)throw new IllegalStateException("Too many tool calls. Ask the user to refine the request.");}
    private JsonNode get(WebClient client,String path,Map<String,String> params){
        limit();String value=client.get().uri(builder->{builder.path(path);params.forEach(builder::queryParam);return builder.build();})
            .headers(h->h.setBearerAuth(identity.token())).retrieve().bodyToMono(String.class).timeout(Duration.ofSeconds(5)).block();
        return new JsonMapper().readTree(value);
    }
    @Tool(description="Find public station codes for a station name or city. Ask the user to select when multiple matches are returned. Never invent a station code.")
    public List<Map<String,String>> findStations(@ToolParam(description="Station name, city or station code") String query){
        if(query==null||query.isBlank()||query.length()>80)return List.of();
        try{
            var result=new ArrayList<Map<String,String>>();
            // Bound catalogue reads to three pages; ask for exact codes when no match is found.
            for(int page=0;page<3&&result.size()<8;page++){
                var rows=get(train,"/api/stations",Map.of("page",String.valueOf(page),"size","100"));
                for(var n:rows.path("content"))if((text(n,"stationCode")+" "+text(n,"stationName")+" "+text(n,"city")).toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT)))
                    result.add(Map.of("code",text(n,"stationCode"),"name",text(n,"stationName"),"city",text(n,"city")));
                if(page+1>=rows.path("totalPages").asInt())break;
            }
            return result.stream().limit(8).toList();
        }catch(Exception e){return List.of(Map.of("notice","Station lookup is unavailable. Ask for the exact station codes or use Find a train."));}
    }
    @Tool(description="Search current train availability and fare using confirmed station codes, an explicit ISO journey date, class and passenger count. Ask for any missing details. Never make a booking.")
    public List<Map<String,Object>> searchTrains(String fromStation,String toStation,String dateOfJourney,String seatClass,int passengers){
        try{
            String from=code(fromStation),to=code(toStation),clazz=seatClass(seatClass);LocalDate date=LocalDate.parse(dateOfJourney),today=LocalDate.now(ZoneId.of("Asia/Kolkata"));
            if(from.equals(to)||date.isBefore(today)||date.isAfter(today.plusMonths(2))||passengers<1||passengers>6)throw new IllegalArgumentException();
            var rows=get(reservation,"/api/reservations/search",Map.of("fromStation",from,"toStation",to,"dateOfJourney",date.toString(),"seatClass",clazz,"passengers",String.valueOf(passengers)));
            var summaries=new ArrayList<Map<String,Object>>();cards.removeIf(c->c.kind().equals("TRAIN"));
            for(var n:rows){if(summaries.size()>=8)break;
                var card=new Card("TRAIN",text(n,"trainName"),null,text(n,"trainNumber"),from,to,date.toString(),clazz,passengers,text(n,"suggestedAllocation"),n.path("totalFare").asDouble(),n.path("availableSeats").asInt(),text(n,"departureAt"),null,null);
                cards.add(card);summaries.add(Map.of("trainNumber",card.trainNumber(),"trainName",card.title(),"allocation",card.status(),"totalFare",card.fare(),"availableSeats",card.availableSeats(),"date",date.toString()));
            }
            return summaries.isEmpty()?List.of(Map.of("notice","No matching trains are available for these details.")):summaries;
        }catch(Exception e){return List.of(Map.of("notice","Search could not complete. Check different station codes, a date within two months, class and 1-6 passengers, or use Find a train."));}
    }
    @Tool(description="Read the signed-in user's booking status using a booking reference such as booking-1 from the current conversation. Raw PNRs are never needed by the model. Only the verified booking card contains private booking details.")
    public Map<String,String> bookingStatus(String bookingReference){
        String pnr=conversation.pnr(bookingReference);
        if(pnr==null)return Map.of("notice","Ask the user to enter PNR followed by their ten-digit PNR. Do not guess a booking reference.");
        try{
            var n=get(reservation,"/api/reservations/my-bookings/"+pnr,Map.of());
            var card=new Card("BOOKING","Your booking",pnr,text(n,"trainNumber"),text(n,"fromStation"),text(n,"toStation"),text(n,"dateOfJourney"),text(n,"seatClass"),n.path("passengers").size(),text(n,"status"),n.path("totalFare").asDouble(),null,text(n,"departureAt"),text(n,"refundStatus"),n.path("refundAmount").asDouble());
            cards.removeIf(c->Objects.equals(c.pnr(),pnr));if(cards.size()<10)cards.add(card);
            // Do not send PNR, passenger names, email, route, dates, money, or seat assignments to Gemini.
            return Map.of("reference",bookingReference,"status",card.status(),"refundStatus",card.refundStatus(),"notice","The verified details are shown locally in a booking card. Use View booking for payment, cancellation or ticket download.");
        }catch(Exception e){return Map.of("notice","This booking could not be retrieved for the signed-in account. Check the PNR or use My journeys. Do not claim that the booking exists or is confirmed.");}
    }
    private String code(String s){if(s==null||!s.strip().matches("[A-Za-z0-9]{1,10}"))throw new IllegalArgumentException();return s.strip().toUpperCase(Locale.ROOT);}
    private String seatClass(String value){
        if(value==null)throw new IllegalArgumentException();String name=value.strip().toUpperCase(Locale.ROOT);
        if(Set.of("1A","2A","3A","3E","2S").contains(name))name="CLASS_"+name;
        if(!Set.of("SL","CLASS_1A","CLASS_2A","CLASS_3A","CLASS_3E","CLASS_2S","EC","UR").contains(name))throw new IllegalArgumentException();
        return name;
    }
    private static String text(JsonNode n,String field){return n.path(field).asText("");}
}
