package com.railway.reservationservice.service;

import com.railway.reservationservice.client.*;
import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.exception.BookingException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/** Read-only, one-interchange planner. Ordinary booking rechecks and holds each leg separately. */
@Service @RequiredArgsConstructor
public class JourneyPlanner {
    private static final ZoneId INDIA=ZoneId.of("Asia/Kolkata");
    private final TrainCatalogClient trains;
    private final StationCatalogClient stations;
    private final ScheduleCatalogClient schedules;
    private final JourneyService journeys;
    private final BookingEngine bookings;
    private final Clock clock;

    public JourneyPlan.Results search(String from,String to,LocalDate date,String seatClass,int count,
                                     boolean nearby,int radius,int minTransfer,int maxTransfer) {
        from=ReservationReq.normalizeStation(from);to=ReservationReq.normalizeStation(to);
        seatClass=seatClass==null?"ALL":seatClass.strip().toUpperCase(Locale.ROOT);
        journeys.validateSearch(from,to,date,"ALL".equals(seatClass)?"SL":seatClass,count);
        if(radius<1||radius>100)throw BookingException.badRequest("Nearby radius must be 1-100 km");
        if(minTransfer<30||minTransfer>240||maxTransfer<minTransfer||maxTransfer>720)
            throw BookingException.badRequest("Minimum transfer must be 30-240 minutes; maximum must be at least the minimum and no more than 720 minutes");
        var context=new Context();
        try {
            CatalogStation source=stations.station(from),destination=stations.station(to);
            if(source==null||destination==null)throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE,"Station catalogue is temporarily unavailable");
            var catalogue=nearby?stationCatalogue(context):List.<CatalogStation>of();
            if(nearby&&catalogue.stream().filter(Objects::nonNull).anyMatch(s->!located(s)))
                context.notices.add("Some stations have no usable location information, so nearby options may be incomplete. Exact-station connections are still searched.");
            catalogue.stream().filter(Objects::nonNull).forEach(s->context.name(s));
            context.name(source);context.name(destination);
            var origins=nearby?near(source,catalogue,radius,context):Map.of(from,0.0);
            var destinations=nearby?near(destination,catalogue,radius,context):Map.of(to,0.0);
            var routes=routes(context,seatClass);
            var departing=new HashMap<String,List<Route>>();
            for(var route:routes)for(int i=0;i<route.stops.size()-1;i++)
                departing.computeIfAbsent(route.stops.get(i).getStationCode(),key->new ArrayList<>()).add(route);
            var plans=new LinkedHashMap<String,JourneyPlan>();
            // Find nearby direct trains at BOTH ends before spending the budget on connections.
            // A busy interchange on an early route must not hide a later direct alternative.
            direct: for(var origin:origins.entrySet())for(var route:departing.getOrDefault(origin.getKey(),List.of())) {
                int start=route.index(origin.getKey());
                for(var target:destinations.entrySet()) {
                    if(route.index(target.getKey())<=start || (origin.getKey().equals(from)&&target.getKey().equals(to)))continue;
                    for(String clazz:classes(route,seatClass)) {
                        if(context.stop())break direct;
                        var leg=quote(context,route,origin.getKey(),target.getKey(),date,clazz,count);
                        if(leg!=null)add(plans,context,List.of(leg),null,origin.getValue(),target.getValue(),true);
                    }
                }
            }
            outer: for(var origin:origins.entrySet())for(var first:departing.getOrDefault(origin.getKey(),List.of())) {
                int start=first.index(origin.getKey());
                for(int end=start+1;end<first.stops.size();end++) {
                    if(context.stop())break outer;
                    String interchange=first.stops.get(end).getStationCode();
                    if(destinations.containsKey(interchange))continue;
                    for(String clazz:classes(first,seatClass)) {
                    JourneyQuote leg1=quote(context,first,origin.getKey(),interchange,date,clazz,count);
                    if(leg1==null)continue;
                    Instant earliest=leg1.getArrivalAt().plusSeconds(minTransfer*60L);
                    Instant latest=leg1.getArrivalAt().plusSeconds(maxTransfer*60L);
                    for(var second:departing.getOrDefault(interchange,List.of())) {
                        if(second.train.getTrainNumber().equals(first.train.getTrainNumber())||!second.train.getSeatClasses().contains(clazz))continue;
                        int change=second.index(interchange);
                        for(var target:destinations.entrySet()) {
                            if(context.stop())break outer;
                            int finish=second.index(target.getKey());
                            if(finish<=change||target.getKey().equals(origin.getKey())||loops(first,start,end,second,change,finish))continue;
                            for(LocalDate day=earliest.atZone(INDIA).toLocalDate();!day.isAfter(latest.atZone(INDIA).toLocalDate());day=day.plusDays(1)) {
                                JourneyQuote leg2=quote(context,second,interchange,target.getKey(),day,clazz,count);
                                if(leg2!=null&&!leg2.getDepartureAt().isBefore(earliest)&&!leg2.getDepartureAt().isAfter(latest))
                                    add(plans,context,List.of(leg1,leg2),interchange,origin.getValue(),target.getValue(),!origin.getKey().equals(from)||!target.getKey().equals(to));
                            }
                        }
                    }
                    }
                }
            }
            var sorted=plans.values().stream().sorted(Comparator
                .comparing((JourneyPlan p)->!p.allocation().equals("CNF"))
                .thenComparingInt(p->p.legs().size())
                .thenComparingDouble(p->p.originDistanceKm()+p.destinationDistanceKm())
                .thenComparing(p->p.legs().getLast().getArrivalAt())
                .thenComparing(JourneyPlan::totalFare).thenComparing(JourneyPlan::id)).toList();
            if(sorted.size()>20)context.limit("Showing the best 20 alternatives; narrow the radius or change the date for other options.");
            var selected=sorted.stream().limit(20).toList();
            var codes=new LinkedHashSet<String>();codes.add(from);codes.add(to);
            selected.forEach(p->p.legs().forEach(q->{codes.add(q.getFromStation());codes.add(q.getToStation());}));
            var names=new LinkedHashMap<String,String>();
            for(String code:codes) {
                if(!context.names.containsKey(code))try { context.name(stations.station(code)); }
                catch(FeignException unavailableName){context.notices.add("Some station names could not be loaded. Station codes remain available.");}
                names.put(code,context.names.getOrDefault(code,code));
            }
            return new JourneyPlan.Results(selected,List.copyOf(context.notices),context.limited,radius,minTransfer,maxTransfer,Map.copyOf(names));
        } catch(FeignException.NotFound missing) {throw new BookingException(HttpStatus.NOT_FOUND,"Boarding or destination station not found");}
        catch(FeignException unavailable) {throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE,"Journey planning catalogue is temporarily unavailable; try again shortly");}
    }
    private List<CatalogStation> stationCatalogue(Context context) {
        var result=new ArrayList<CatalogStation>();
        for(int page=0;page<100;page++) {
            var batch=stations.page(page,100);
            if(batch==null||batch.content()==null)throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE,"Station catalogue is temporarily unavailable");
            result.addAll(batch.content());if(page+1>=batch.totalPages())return result;
            if(context.stop())return result;
        }
        context.limit("Nearby search examined the first 10,000 catalogue stations.");return result;
    }
    private Map<String,Double> near(CatalogStation origin,List<CatalogStation> catalogue,int radius,Context context) {
        var result=new LinkedHashMap<String,Double>();result.put(origin.stationCode(),0.0);
        if(!located(origin)){context.notices.add("Nearby alternatives for "+origin.stationCode()+" require coordinates in the station catalogue. Exact-station connections are still searched.");return result;}
        var nearby=catalogue.stream().filter(Objects::nonNull).filter(s->s.stationCode()!=null&&!s.stationCode().equals(origin.stationCode())&&located(s))
            .map(s->Map.entry(s.stationCode(),distanceKm(origin,s))).filter(e->e.getValue()<=radius)
            .sorted(Map.Entry.<String,Double>comparingByValue().thenComparing(Map.Entry.comparingByKey())).toList();
        nearby.forEach(e->result.putIfAbsent(e.getKey(),e.getValue()));return result;
    }
    static boolean located(CatalogStation s){return s.latitude()!=null&&s.longitude()!=null&&Double.isFinite(s.latitude())&&Double.isFinite(s.longitude())&&Math.abs(s.latitude())<=90&&Math.abs(s.longitude())<=180&&!(s.latitude()==0&&s.longitude()==0);}
    static double distanceKm(CatalogStation a,CatalogStation b) {
        double lat=Math.toRadians(b.latitude()-a.latitude()),lon=Math.toRadians(b.longitude()-a.longitude());
        double h=Math.pow(Math.sin(lat/2),2)+Math.cos(Math.toRadians(a.latitude()))*Math.cos(Math.toRadians(b.latitude()))*Math.pow(Math.sin(lon/2),2);
        return 6371.0088*2*Math.asin(Math.sqrt(Math.min(1,Math.max(0,h))));
    }
    private List<Route> routes(Context context,String seatClass) {
        var result=new ArrayList<Route>();
        for(int page=0;page<100;page++) {
            var batch=trains.getPage(page,100);
            if(batch==null||batch.content()==null)throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE,"Train catalogue is temporarily unavailable");
            for(var train:batch.content()) {
                if(context.stop())return result;
                try {
                    journeys.validateTrainConfiguration(train);
                    if(!"ALL".equals(seatClass)&&!train.getSeatClasses().contains(seatClass))continue;
                    var stops=schedules.getSchedule(train.getTrainNumber());journeys.validateRoute(stops);
                    result.add(new Route(train,stops));
                }catch(BookingException invalid){if(invalid.getStatus()!=HttpStatus.CONFLICT)throw invalid;}
                catch(FeignException.NotFound unpublished){/* No published timetable: not bookable. */}
            }
            if(page+1>=batch.totalPages())return result;
        }
        context.limit("Planning examined the first 10,000 catalogue trains. Results may be incomplete.");return result;
    }
    private List<String> classes(Route route,String requested) {
        return "ALL".equals(requested)?route.train.getSeatClasses().stream().sorted().toList():List.of(requested);
    }
    private JourneyQuote quote(Context context,Route route,String from,String to,LocalDate date,String seatClass,int count) {
        if(date.isAfter(LocalDate.now(clock.withZone(INDIA)).plusMonths(2))||context.stop())return null;
        String key=route.train.getTrainNumber()+"|"+from+"|"+to+"|"+date+"|"+seatClass;
        if(context.quotes.containsKey(key))return context.quotes.get(key);
        JourneyQuote q=null;
        try{q=journeys.quoteRoute(route.train,route.stops,from,to,date,seatClass,count);}
        catch(BookingException e){if(e.getStatus()!=HttpStatus.CONFLICT)throw e;}
        context.quotes.put(key,q);return q;
    }
    private void add(Map<String,JourneyPlan> plans,Context context,List<JourneyQuote> legs,String interchange,double originKm,double destinationKm,boolean nearby) {
        String id=legs.stream().map(q->q.getTrainNumber()+"-"+q.getFromStation()+"-"+q.getToStation()+"-"+q.getBoardingDate()+"-"+q.getSeatClass()).reduce((a,b)->a+"_"+b).orElseThrow();
        if(plans.containsKey(id))return;
        for(var q:legs) {
            if(q.getSuggestedAllocation()==null)bookings.describeAvailability(q);
            if("UNAVAILABLE".equals(q.getSuggestedAllocation()))return;
        }
        String kind=legs.size()==2?(nearby?"NEARBY_CONNECTION":"CONNECTION"):"NEARBY_DIRECT";
        var total=legs.stream().map(JourneyQuote::getTotalFare).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
        long wait=legs.size()==2?Duration.between(legs.getFirst().getArrivalAt(),legs.getLast().getDepartureAt()).toMinutes():0;
        plans.put(id,new JourneyPlan(id,kind,legs,interchange,wait,Duration.between(legs.getFirst().getDepartureAt(),legs.getLast().getArrivalAt()).toMinutes(),total,
            legs.stream().allMatch(q->"CNF".equals(q.getSuggestedAllocation()))?"CNF":"WL",round(originKm),round(destinationKm)));
        context.found=plans.size();
    }
    private static double round(double n){return Math.round(n*10.0)/10.0;}
    private boolean loops(Route first,int start,int end,Route second,int change,int finish) {
        Set<String> visited=new HashSet<>();for(int i=start;i<end;i++)visited.add(first.stops.get(i).getStationCode());
        for(int i=change+1;i<=finish;i++)if(visited.contains(second.stops.get(i).getStationCode()))return true;
        return false;
    }
    private record Route(CatalogTrain train,List<CatalogStop> stops) {
        int index(String code){for(int i=0;i<stops.size();i++)if(stops.get(i).getStationCode().equals(code))return i;return -1;}
    }
    private static class Context {
        final long deadline=System.nanoTime()+Duration.ofSeconds(12).toNanos();
        final Map<String,JourneyQuote> quotes=new HashMap<>();final Set<String> notices=new LinkedHashSet<>();
        final Map<String,String> names=new HashMap<>();
        void name(CatalogStation s){if(s!=null&&s.stationCode()!=null&&s.stationName()!=null&&!s.stationName().isBlank())names.put(s.stationCode(),s.stationName());}
        boolean limited;int found;int scans;
        void limit(String message){limited=true;notices.add(message);}
        boolean stop(){if(found>=1000||quotes.size()>=10000||++scans>100000||System.nanoTime()>deadline){limit("Planning reached its search limit. These are partial results; try a smaller radius or another date.");return true;}return false;}
    }
}
