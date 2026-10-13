package com.railway.reservationservice.service;
import com.railway.reservationservice.client.*;
import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.exception.BookingException;
import org.junit.jupiter.api.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JourneyPlannerTest {
    final TrainCatalogClient trains=mock(TrainCatalogClient.class);
    final StationCatalogClient stations=mock(StationCatalogClient.class);
    final ScheduleCatalogClient schedules=mock(ScheduleCatalogClient.class);
    final BookingEngine engine=mock(BookingEngine.class);
    final Clock clock=Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"),ZoneOffset.UTC);
    final LocalDate date=LocalDate.of(2030,1,2);
    final JourneyService quotes=new JourneyService(trains,schedules,clock,mock(com.railway.reservationservice.repository.TrainRunCancellationRepository.class));
    final JourneyPlanner planner=new JourneyPlanner(trains,stations,schedules,quotes,engine,clock);
    List<CatalogTrain> catalogue;
    @BeforeEach void setup(){
        catalogue=new ArrayList<>();when(trains.getPage(0,100)).thenAnswer(i->new PageResult<>(catalogue,0,100,catalogue.size(),1));
        when(stations.station("AAA")).thenReturn(station("AAA",19,73));when(stations.station("CCC")).thenReturn(station("CCC",15,74));
        when(stations.page(0,100)).thenReturn(new PageResult<>(List.of(station("AAA",19,73),station("CCC",15,74)),0,100,2,1));
        doAnswer(i->{JourneyQuote q=i.getArgument(0);q.setSuggestedAllocation("CNF");q.setAvailableSeats(10);return null;}).when(engine).describeAvailability(any());
        route("T1",List.of(stop("AAA",1,1,"10:00",0),stop("XXX",2,1,"12:00",100)));
        route("T2",List.of(stop("XXX",1,1,"13:00",0),stop("CCC",2,1,"15:00",100)));
    }
    static CatalogStation station(String code,double lat,double lon){return new CatalogStation(code,code,lat,lon);}
    static CatalogTrain train(String number){
        var t=new CatalogTrain();t.setTrainNumber(number);t.setTrainName("Train "+number);t.setOperatingDays(EnumSet.allOf(DayOfWeek.class));t.setSeatClasses(Set.of("SL"));
        var c=new CatalogTrain.ClassConfiguration();c.setSeatClass("SL");c.setCoachCount(1);c.setSeatsPerCoach(10);c.setBaseFare(BigDecimal.TEN);c.setRatePerKm(BigDecimal.ONE);c.setMinimumFare(BigDecimal.TEN);t.setClassConfigurations(List.of(c));return t;
    }
    static CatalogStop stop(String code,int sequence,int day,String time,int distance){
        var s=new CatalogStop();s.setStationCode(code);s.setStopSequence(sequence);s.setDayNumber(day);s.setDepartureDayNumber(day);s.setArrivalTime(LocalTime.parse(time));s.setDepartureTime(LocalTime.parse(time));s.setDistanceKm(distance);return s;
    }
    void route(String number,List<CatalogStop> stops){catalogue.removeIf(t->t.getTrainNumber().equals(number));catalogue.add(train(number));when(schedules.getSchedule(number)).thenReturn(stops);}
    JourneyPlan.Results search(boolean nearby){return planner.search(" aaa ","ccc",date,"SL",2,nearby,50,60,360);}
    @Test void connectsAndQuotesBothLegsWithoutCreatingBookings(){
        var result=search(false);assertEquals(1,result.options().size());var p=result.options().getFirst();assertEquals("CONNECTION",p.kind());assertEquals(60,p.transferMinutes());assertEquals(300,p.durationMinutes());assertEquals(new BigDecimal("440.00"),p.totalFare());assertEquals("CNF",p.allocation());
        verify(schedules,times(1)).getSchedule("T1");verify(schedules,times(1)).getSchedule("T2");verify(engine,times(2)).describeAvailability(any());verifyNoMoreInteractions(engine);
    }
    @Test void enforcesMinimumAndMaximumBuffers(){
        route("T2",List.of(stop("XXX",1,1,"12:59",0),stop("CCC",2,1,"15:00",100)));assertTrue(search(false).options().isEmpty());
        route("T2",List.of(stop("XXX",1,1,"18:01",0),stop("CCC",2,1,"20:00",100)));assertTrue(search(false).options().isEmpty());
        route("T2",List.of(stop("XXX",1,1,"18:00",0),stop("CCC",2,1,"20:00",100)));assertEquals(360,search(false).options().getFirst().transferMinutes());
    }
    @Test void overnightInterchangeUsesSecondBoardingDateAndOriginalTrainRunDay(){
        route("T1",List.of(stop("AAA",1,1,"22:00",0),stop("XXX",2,1,"23:30",100)));
        route("T2",List.of(stop("BBB",1,1,"23:45",0),stop("XXX",2,2,"00:45",50),stop("CCC",3,2,"03:00",150)));
        catalogue.getLast().setOperatingDays(Set.of(date.getDayOfWeek()));
        var p=search(false).options().getFirst();assertEquals(75,p.transferMinutes());assertEquals(date.plusDays(1),p.legs().getLast().getBoardingDate());assertEquals(date,p.legs().getLast().getRunDate());
        catalogue.getLast().setOperatingDays(Set.of(date.plusDays(1).getDayOfWeek()));assertTrue(search(false).options().isEmpty());
    }
    @Test void nearbyAlternativesCanChangeBothEndpointsAndExcludeOutOfRadiusStations(){
        var nearby=List.of(station("AAN",19.1,73),station("CCN",15.1,74),station("FAR",25,80));
        when(stations.page(0,100)).thenReturn(new PageResult<>(nearby,0,100,3,1));
        route("T3",List.of(stop("AAN",1,1,"09:00",0),stop("CCN",2,1,"14:00",200)));
        route("T4",List.of(stop("FAR",1,1,"09:00",0),stop("CCC",2,1,"14:00",200)));
        var p=search(true).options().stream().filter(x->x.kind().equals("NEARBY_DIRECT")).findFirst().orElseThrow();
        assertEquals("AAN",p.legs().getFirst().getFromStation());assertEquals("CCN",p.legs().getFirst().getToStation());assertEquals(11.1,p.originDistanceKm(),.1);assertEquals(11.1,p.destinationDistanceKm(),.1);
        assertTrue(search(true).options().stream().noneMatch(x->x.legs().getFirst().getTrainNumber().equals("T4")));
        assertEquals(1,search(false).options().size());
    }
    @Test void nearbyConnectionsAndColocatedStationCodesRemainClearlyMarked(){
        when(stations.page(0,100)).thenReturn(new PageResult<>(List.of(station("AAN",19,73)),0,100,1,1));
        route("T3",List.of(stop("AAN",1,1,"09:00",0),stop("XXX",2,1,"12:00",200)));
        assertTrue(search(true).options().stream().anyMatch(p->p.kind().equals("NEARBY_CONNECTION")&&p.originDistanceKm()==0));
    }
    @Test void missingCoordinatesExplainLimitationButExactConnectionsRemain(){
        when(stations.station("AAA")).thenReturn(new CatalogStation("AAA","AAA",null,null));var r=search(true);
        assertEquals(1,r.options().size());assertTrue(r.notices().stream().anyMatch(s->s.contains("AAA")&&s.contains("coordinates")));
    }
    @Test void rejectsLoopsAndDoesNotSplitSameTrainIntoFakeConnection(){
        route("T2",List.of(stop("XXX",1,1,"13:00",0),stop("AAA",2,1,"14:00",100),stop("CCC",3,1,"15:00",200)));
        assertTrue(search(false).options().isEmpty());
        catalogue.clear();route("T1",List.of(stop("AAA",1,1,"10:00",0),stop("XXX",2,1,"12:00",100),stop("CCC",3,1,"15:00",200)));assertTrue(search(false).options().isEmpty());
    }
    @Test void waitlistOnEitherLegLabelsWholeSuggestionAndUnavailableLegIsRemoved(){
        doAnswer(i->{JourneyQuote q=i.getArgument(0);q.setSuggestedAllocation(q.getTrainNumber().equals("T2")?"WL":"CNF");return null;}).when(engine).describeAvailability(any());assertEquals("WL",search(false).options().getFirst().allocation());
        doAnswer(i->{JourneyQuote q=i.getArgument(0);q.setSuggestedAllocation("UNAVAILABLE");return null;}).when(engine).describeAvailability(any());assertTrue(search(false).options().isEmpty());
    }
    @Test void secondLegCannotExceedBookingWindow(){
        route("T1",List.of(stop("AAA",1,1,"22:00",0),stop("XXX",2,1,"23:30",100)));
        route("T2",List.of(stop("XXX",1,1,"00:45",0),stop("CCC",2,1,"03:00",100)));
        assertTrue(planner.search("AAA","CCC",LocalDate.of(2030,3,1),"SL",1,false,50,60,360).options().isEmpty());
    }
    @Test void invalidInputsDoNotReadRemoteCatalogue(){
        assertThrows(BookingException.class,()->planner.search("AAA","AAA",date,"SL",1,false,50,60,360));
        assertThrows(BookingException.class,()->planner.search("AAA","CCC",date,"INVALID",1,false,50,60,360));
        assertThrows(BookingException.class,()->planner.search("AAA","CCC",date,"SL",-1,false,50,60,360));
        assertThrows(BookingException.class,()->planner.search("AAA","CCC",date,"SL",1,false,101,60,360));
        assertThrows(BookingException.class,()->planner.search("AAA","CCC",date,"SL",1,false,50,15,360));
        assertThrows(BookingException.class,()->planner.search("AAA","CCC",LocalDate.of(2029,12,31),"SL",1,false,50,60,360));
        verifyNoInteractions(stations);
    }

    @Test void mumbaiToDelhiIncludesNewDelhiDestinationAndAcOnlyTrainsByDefault(){
        catalogue.clear();
        var mmct=new CatalogStation("MMCT","Mumbai Central",18.9697,72.8194);
        var dli=new CatalogStation("DLI","Delhi Junction",28.661,77.228);
        var ndls=new CatalogStation("NDLS","New Delhi",28.6428,77.21908);
        when(stations.station("MMCT")).thenReturn(mmct);when(stations.station("DLI")).thenReturn(dli);
        when(stations.page(0,100)).thenReturn(new PageResult<>(List.of(mmct,dli,ndls),0,100,3,1));
        route("12951",List.of(stop("MMCT",1,1,"16:00",0),stop("NDLS",2,2,"08:00",1384)));
        catalogue.getFirst().setSeatClasses(Set.of("CLASS_3A"));catalogue.getFirst().getClassConfigurations().getFirst().setSeatClass("CLASS_3A");
        var result=planner.search("MMCT","DLI",date,"ALL",1,true,50,60,360);
        var p=result.options().getFirst();assertEquals("NEARBY_DIRECT",p.kind());
        assertEquals("MMCT",p.legs().getFirst().getFromStation());assertEquals("NDLS",p.legs().getFirst().getToStation());
        assertEquals("CLASS_3A",p.legs().getFirst().getSeatClass());assertEquals(0,p.originDistanceKm());assertTrue(p.destinationDistanceKm()>1&&p.destinationDistanceKm()<3);
        assertEquals("New Delhi",result.stationNames().get("NDLS"));assertEquals("Delhi Junction",result.stationNames().get("DLI"));
        assertTrue(planner.search("MMCT","DLI",date,"SL",1,true,50,60,360).options().isEmpty());
        assertTrue(planner.search("MMCT","DLI",date,"ALL",1,false,50,60,360).options().isEmpty());
        assertTrue(planner.search("MMCT","DLI",date,"ALL",1,true,1,60,360).options().isEmpty());
    }

    @Test void usableStationsBeyondFourNearestAreSearchedAtBothEnds(){
        catalogue.clear();var nearby=new ArrayList<CatalogStation>();
        for(int i=1;i<=5;i++){nearby.add(station("A"+i,19+i*.01,73));nearby.add(station("C"+i,15+i*.01,74));}
        nearby.add(station("PNVL",19.1,73));nearby.add(station("NZM",15.1,74));
        when(stations.page(0,100)).thenReturn(new PageResult<>(nearby,0,100,nearby.size(),1));
        route("T3",List.of(stop("PNVL",1,1,"09:00",0),stop("NZM",2,1,"14:00",200)));
        var result=search(true);assertEquals(1,result.options().size());
        var p=result.options().getFirst();assertEquals("PNVL",p.legs().getFirst().getFromStation());assertEquals("NZM",p.legs().getFirst().getToStation());
        assertFalse(result.limited());
    }

    @Test void everyClassKeepsItsOwnFareAvailabilityAndId(){
        catalogue.clear();when(stations.page(0,100)).thenReturn(new PageResult<>(List.of(station("CCN",15.1,74)),0,100,1,1));
        route("T3",List.of(stop("AAA",1,1,"09:00",0),stop("CCN",2,1,"14:00",200)));
        var t=catalogue.getFirst();var ac=train("unused").getClassConfigurations().getFirst();ac.setSeatClass("CLASS_3A");ac.setBaseFare(new BigDecimal("500"));
        t.setSeatClasses(Set.of("SL","CLASS_3A"));t.setClassConfigurations(List.of(t.getClassConfigurations().getFirst(),ac));
        doAnswer(i->{JourneyQuote q=i.getArgument(0);q.setSuggestedAllocation(q.getSeatClass().equals("SL")?"WL":"CNF");return null;}).when(engine).describeAvailability(any());
        var options=planner.search("AAA","CCC",date,"ALL",1,true,50,60,360).options();
        assertEquals(2,options.size());assertEquals("CLASS_3A",options.getFirst().legs().getFirst().getSeatClass());
        assertEquals(new BigDecimal("700.00"),options.getFirst().totalFare());assertEquals(new BigDecimal("210.00"),options.getLast().totalFare());
        assertNotEquals(options.getFirst().id(),options.getLast().id());verify(schedules,times(1)).getSchedule("T3");
    }

    @Test void connectionsCanChangeBothEndpointsAndResolveInterchangeName(){
        catalogue.clear();when(stations.page(0,100)).thenReturn(new PageResult<>(List.of(station("AAN",19.1,73),station("CCN",15.1,74),new CatalogStation("ST","Surat",21.204,72.841)),0,100,3,1));
        route("T3",List.of(stop("AAN",1,1,"09:00",0),stop("ST",2,1,"12:00",200)));
        route("T4",List.of(stop("ST",1,1,"13:00",0),stop("CCN",2,1,"18:00",500)));
        var result=planner.search("AAA","CCC",date,"ALL",1,true,50,60,360);
        assertEquals("NEARBY_CONNECTION",result.options().getFirst().kind());assertEquals("Surat",result.stationNames().get("ST"));
        assertEquals(60,result.options().getFirst().transferMinutes());
    }

    @Test void routesAfterTheSecondCataloguePageAreNotSilentlyDropped(){
        catalogue.clear();route("LATE",List.of(stop("AAA",1,1,"09:00",0),stop("CCN",2,1,"14:00",200)));
        when(stations.page(0,100)).thenReturn(new PageResult<>(List.of(station("CCN",15.1,74)),0,100,1,1));
        when(trains.getPage(0,100)).thenReturn(new PageResult<>(List.of(),0,100,201,3));
        when(trains.getPage(1,100)).thenReturn(new PageResult<>(List.of(),1,100,201,3));
        when(trains.getPage(2,100)).thenReturn(new PageResult<>(catalogue,2,100,201,3));
        assertEquals("LATE",search(true).options().getFirst().legs().getFirst().getTrainNumber());
        verify(trains).getPage(2,100);
    }

    @Test void zeroCoordinatePlaceholderDoesNotPretendToBeALocation(){
        when(stations.station("CCC")).thenReturn(new CatalogStation("CCC","Destination",0.0,0.0));
        var result=search(true);assertEquals(1,result.options().size());assertTrue(result.notices().stream().anyMatch(n->n.contains("CCC")&&n.contains("coordinates")));
        assertFalse(JourneyPlanner.located(new CatalogStation("BAD","Bad",Double.NaN,75.0)));
    }
}
