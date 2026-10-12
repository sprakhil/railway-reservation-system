package com.railway.reservationservice.service;

import com.railway.reservationservice.dto.SupportTypes.*;
import com.railway.reservationservice.entity.SupportTicket.*;
import com.railway.reservationservice.entity.Reservation;
import com.railway.reservationservice.exception.BookingException;
import com.railway.reservationservice.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:support;MODE=MySQL;DB_CLOSE_DELAY=-1","support.email=support@example.test"})
@AutoConfigureMockMvc
class SupportIntegrationTest {
    @Autowired SupportService support;@Autowired SupportTicketRepository tickets;@Autowired SupportMessageRepository messages;
    @Autowired BookingNotificationRepository outbox;@Autowired UserNotificationRepository inbox;@Autowired MockMvc mvc;
    @MockitoBean ReservationRepository bookings;
    @MockitoBean java.time.Clock clock;
    @BeforeEach void clear(){when(clock.instant()).thenReturn(java.time.Instant.parse("2030-01-01T00:00:00.123456789Z"));messages.deleteAll();tickets.deleteAll();outbox.deleteAll();inbox.deleteAll();}
    Create request(String pnr){return new Create(UUID.randomUUID().toString(),Category.BOOKING,"Help with my journey",pnr,"Please check the booking update.");}
    Ticket create(){return support.create("alice","alice@example.test",request(null));}

    @Test void ownerIsolationReplayAndDurableNotifications(){
        var req=request(null);var t=support.create("alice","alice@example.test",req);
        assertEquals(t.id(),support.create("alice","alice@example.test",req).id());assertEquals(1,tickets.count());assertEquals(1,messages.count());
        assertEquals(2,outbox.count());assertEquals(1,inbox.count());assertEquals(t.id(),inbox.findAll().getFirst().getSupportTicketId());assertNull(inbox.findAll().getFirst().getPnr());
        assertEquals(0,support.list("bob",false,0,20,null,null,null).totalElements());
        assertThrows(BookingException.class,()->support.get(t.id(),"bob",false));
        assertThrows(BookingException.class,()->support.conversation(t.id(),"bob",false,0,20));
        assertThrows(BookingException.class,()->support.reply(t.id(),"bob",false,new Reply(UUID.randomUUID().toString(),"Not mine")));
        assertThrows(BookingException.class,()->support.create("alice","alice@example.test",new Create(req.requestId(),Category.OTHER,"Different request",null,"Another message")));
        assertNull(support.get(t.id(),"alice",false).email());assertEquals("alice@example.test",support.get(t.id(),"admin",true).email());
    }
    @Test void linkedBookingMustBelongToUserEvenWhenUserIsAdmin(){
        var booking=new Reservation();booking.setUsername("alice");when(bookings.findByPnr("1234567890")).thenReturn(Optional.of(booking));
        assertEquals("1234567890",support.create("alice","alice@example.test",request("1234567890")).pnr());
        assertThrows(BookingException.class,()->support.create("admin","admin@example.test",request("1234567890")));
        assertThrows(BookingException.class,()->support.create("alice","alice@example.test",request("0987654321")));
        assertEquals(1,tickets.count());
    }
    @Test void replyResolutionReopenAndStaleStatusHaveAuditAndNoDuplicateEvents(){
        var t=create();var reply=new Reply(UUID.randomUUID().toString(),"We are checking this for you.");
        var active=support.reply(t.id(),"operator",true,reply);assertEquals(Status.IN_PROGRESS,active.status());
        assertEquals(active.version(),support.reply(t.id(),"operator",true,reply).version());assertEquals(2,messages.count());assertEquals(2,inbox.count());
        assertThrows(BookingException.class,()->support.status(t.id(),"operator",new ChangeStatus(Status.RESOLVED,t.version())));
        var resolved=support.status(t.id(),"operator",new ChangeStatus(Status.RESOLVED,active.version()));assertEquals(Status.RESOLVED,resolved.status());
        var open=support.reply(t.id(),"alice",false,new Reply(UUID.randomUUID().toString(),"I still need help."));assertEquals(Status.OPEN,open.status());
        var conversation=support.conversation(t.id(),"alice",false,0,20).content();
        assertEquals(List.of("I still need help.", "Status changed to resolved.", "We are checking this for you.", "Please check the booking update."), conversation.stream().map(Message::body).toList());
        assertEquals(4, conversation.stream().map(Message::createdAt).distinct().count());assertTrue(conversation.stream().anyMatch(Message::systemMessage));
        assertTrue(conversation.stream().filter(Message::staff).allMatch(m->m.author().equals("Railwize support")));
        assertEquals(4,messages.count());assertEquals(3,inbox.count());
    }
    @Test void boundedFiltersAndLiteralSearch(){
        create();support.create("bob","bob@example.test",new Create(UUID.randomUUID().toString(),Category.ACCOUNT,"Username question",null,"How do I change it?"));
        assertEquals(1,support.list("admin",true,0,20,"OPEN","ACCOUNT","bob@example").totalElements());
        assertEquals(0,support.list("admin",true,0,20,null,null,"%").totalElements());
        assertThrows(BookingException.class,()->support.list("alice",false,0,51,null,null,null));
        assertThrows(BookingException.class,()->support.list("alice",false,0,20,"BOGUS",null,null));
    }
    @Test void httpRequiresAuthenticationAndAdminRoleAndValidInput() throws Exception {
        mvc.perform(get("/api/reservations/support")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reservations/admin/support").with(user("alice").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reservations/admin/support").with(user("operator").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/api/reservations/support").with(user("alice")).requestAttr("loggedInUserEmail","alice@example.test")
            .contentType("application/json").content("{\"requestId\":\""+UUID.randomUUID()+"\",\"category\":\"ACCOUNT\",\"subject\":\"   \",\"message\":\"Help\"}"))
            .andExpect(status().isBadRequest());
        var t=create();mvc.perform(get("/api/reservations/support/"+t.id()).with(user("bob"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/reservations/admin/support/"+t.id()+"/status").with(user("alice")).contentType("application/json").content("{\"status\":\"RESOLVED\",\"version\":0}"))
            .andExpect(status().isForbidden());
    }
}
