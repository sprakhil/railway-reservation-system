package com.railway.reservationservice.service;
import com.railway.reservationservice.entity.Reservation;
import com.railway.reservationservice.repository.ReservationRepository;
import com.railway.reservationservice.service.AdminBookingService.Filter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;import java.math.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:admin-report;MODE=MySQL;DB_CLOSE_DELAY=-1") @AutoConfigureMockMvc
class AdminBookingIntegrationTest {
 @Autowired ReservationRepository repository;@Autowired AdminBookingService service;@Autowired MockMvc mvc;
 final LocalDate jan=LocalDate.of(2030,1,2),feb=LocalDate.of(2030,2,2);
 private Reservation booking(String pnr,String train,LocalDate day,String status,boolean paid,String refund,BigDecimal amount){
  return repository.saveAndFlush(Reservation.builder().pnr(pnr).username("alice").userEmail("alice@example.test").trainNumber(train).dateOfJourney(day).bookingTime(day.minusDays(1).atTime(10,0)).status(Reservation.ReservationStatus.valueOf(status)).dataVersion(2).totalFare(new BigDecimal("100.00")).razorpayPaymentId(paid?"pay_"+pnr:null).refundStatus(refund).refundAmount(amount).cancellationFee(BigDecimal.ZERO).fromStation("AAA").toStation("BBB").build());
 }
 @BeforeEach void seed(){repository.deleteAll();booking("1000000001","T1",jan,"CONFIRMED",true,"NONE",BigDecimal.ZERO);booking("1000000002","T1",jan,"CANCELLED",true,"PROCESSED",new BigDecimal("80.00"));booking("1000000003","T2",feb,"CANCELLED",true,"PENDING",new BigDecimal("80.00"));booking("1000000004","T2",feb,"PENDING_PAYMENT",false,"NONE",BigDecimal.ZERO);booking("1000000005","T2",feb,"WAITING",true,"NONE",BigDecimal.ZERO);}
 @Test void totalsExcludeUnpaidFaresAndDistinguishRefundStates(){var d=service.dashboard();assertEquals(5,d.totals().bookings());assertEquals(2,d.totals().cancellations());assertEquals(4,d.totals().paidBookings());assertEquals(40.0,d.totals().cancellationRate());assertEquals(new BigDecimal("400.00"),d.totals().gross());assertEquals(new BigDecimal("80.00"),d.totals().refunded());assertEquals(new BigDecimal("80.00"),d.totals().outstandingRefunds());assertEquals(new BigDecimal("240.00"),d.totals().net());assertEquals("T1",d.popularTrains().getFirst().label());assertEquals(2,d.popularTrains().getFirst().paidBookings());}
 @Test void monthDateAndTrainFiltersUseJourneyDates(){var report=service.report(jan,feb,"","month");assertEquals(2,report.rows().size());assertEquals("2030-01",report.rows().getFirst().label());assertEquals(2,report.rows().getFirst().bookings());var one=service.report(jan,jan,"T1","day");assertEquals(2,one.totals().bookings());assertEquals(1,one.rows().size());assertEquals(3,service.report(jan,feb,"T2","train").totals().bookings());assertThrows(RuntimeException.class,()->service.report(jan,jan.plusDays(366),"","day"));assertThrows(RuntimeException.class,()->service.report(feb,jan,"","day"));}
 @Test void paginationFiltersAndStableSorting(){var f=new Filter("alice","alice","T2","CANCELLED",jan,feb);var p=service.list(f,0,1,"pnr","desc");assertEquals(1,p.totalElements());assertEquals("1000000003",p.content().getFirst().pnr());assertEquals(0,service.list(new Filter("%",null,null,null,null,null),0,20,"pnr","asc").totalElements());assertThrows(RuntimeException.class,()->service.list(f,0,101,"pnr","asc"));assertThrows(RuntimeException.class,()->service.list(f,0,20,"password","asc"));}
 @Test void emptyDashboardAndReportReturnZeroNotFailure(){repository.deleteAll();var d=service.dashboard();assertEquals(0,d.totals().bookings());assertEquals(0,d.totals().gross().signum());assertTrue(d.popularTrains().isEmpty());assertTrue(service.report(jan,feb,"","month").rows().isEmpty());}
 @Test void legacyUnverifiedFaresAreNotCounted(){var r=booking("1000000006","T3",jan,"CONFIRMED",true,"NONE",BigDecimal.ZERO);r.setDataVersion(0);repository.saveAndFlush(r);assertEquals(new BigDecimal("400.00"),service.dashboard().totals().gross());}
 @Test void csvExportsMatchFiltersAndQuoteDangerousText(){var r=repository.findByPnr("1000000003").orElseThrow();r.setUsername("=SUM(1,2)");repository.saveAndFlush(r);String csv=service.export(new Filter(null,null,"T2",null,jan,feb),true);assertTrue(csv.startsWith("\uFEFFPNR"));assertTrue(csv.contains("\"'=SUM(1,2)\""));assertTrue(csv.contains("1000000003"));assertFalse(csv.contains("1000000004"));assertEquals("\"a\"\"b\"",AdminBookingService.cell("a\"b"));String report=service.reportCsv(jan,feb,"T1","month");assertTrue(report.contains("2030-01"));assertFalse(report.contains("2030-02\","));}
 @Test void allAdminEndpointsDenyPassengersAndAnonymousUsers()throws Exception{
  for(String path:new String[]{"/dashboard","/bookings","/reports?fromDate=2030-01-01&toDate=2030-02-01","/bookings/export","/reports/export?fromDate=2030-01-01&toDate=2030-02-01"}){
   mvc.perform(get("/api/reservations/admin"+path)).andExpect(status().isUnauthorized());mvc.perform(get("/api/reservations/admin"+path).with(user("alice").roles("PASSENGER"))).andExpect(status().isForbidden());
  }
  mvc.perform(get("/api/reservations/admin/dashboard").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.totals.bookings").value(5));
  mvc.perform(get("/api/reservations/admin/bookings/export").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
 }
}
