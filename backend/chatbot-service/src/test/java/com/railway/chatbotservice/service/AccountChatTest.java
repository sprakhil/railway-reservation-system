package com.railway.chatbotservice.service;

import com.railway.chatbotservice.config.SecurityConfig.Identity;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountChatTest {
    HttpServer server;String url;AtomicReference<String> request=new AtomicReference<>(),auth=new AtomicReference<>();
    String payload="""
        {"content":[{"pnr":"1234567890","trainNumber":"12345","status":"CANCELLED","fromStation":"AAA","toStation":"BBB","dateOfJourney":"2030-01-02","departureAt":"2030-01-02T04:30:00Z","passengers":[{"name":"PRIVATE NAME"}]}],"totalElements":19,"totalPages":3}
        """;
    int status=200;
    @BeforeEach void setup()throws Exception{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{request.set(e.getRequestURI().toString());auth.set(e.getRequestHeaders().getFirst("Authorization"));byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);e.close();});server.start();url="http://127.0.0.1:"+server.getAddress().getPort();
    }
    @AfterEach void stop(){server.stop(0);}
    RailwayTools tools(){var client=WebClient.builder().baseUrl(url).build();return new RailwayTools(client,client,new Identity("alice","private-token"),new Conversations.State("alice"));}
    @Test void ownerTokenPaginationAndPrivateCardsStayLocal(){
        var tools=tools();var result=tools.myBookings("CANCELLED",1);
        assertTrue(request.get().contains("status=CANCELLED"));assertTrue(request.get().contains("page=1"));assertEquals("Bearer private-token",auth.get());
        assertEquals(19L,result.get("totalBookings"));assertEquals("1234567890",tools.cards().getFirst().pnr());
        for(String secret:new String[]{"1234567890","PRIVATE NAME","AAA","2030"})assertFalse(result.toString().contains(secret));
    }
    @Test void countsAndNextUseOwnerOverviewAndNeverGuessOnFailure(){
        payload="{\"totalBookings\":31,\"byStatus\":{\"CONFIRMED\":20,\"CANCELLED\":11},\"nextJourney\":null}";
        var tools=tools();assertTrue(tools.myBookings("COUNT",0).get("notice").toString().contains("31 booking records"));
        assertTrue(tools.myBookings("NEXT",0).get("notice").toString().contains("no upcoming"));assertTrue(tools.cards().isEmpty());
        status=503;assertTrue(tools.myBookings("ALL",0).get("notice").toString().contains("does not mean"));
    }
    @Test void nextWaitlistedCardDoesNotClaimConfirmedTravel(){
        payload="{\"nextJourney\":{\"pnr\":\"1234567890\",\"status\":\"WAITING\"}}";
        var tools=tools();assertTrue(tools.myBookings("NEXT",0).get("notice").toString().contains("travel is not confirmed"));assertEquals(1,tools.cards().size());
    }
    @Test void commonAccountQuestionWorksWithNoGeminiAndNoPnr()throws Exception{
        var ai=mock(AiModels.class);var conversations=new Conversations();var state=conversations.create("alice");
        var chat=new ChatService(ai,conversations,new Guardrails(),new KnowledgeBase(ai),new SemanticCache(),url,url);
        var events=chat.ask(new Identity("alice","private-token"),state.id,"Which tickets I have cancelled?").collectList().block(Duration.ofSeconds(5));
        assertTrue(events.stream().anyMatch(e->e.text().contains("19 cancelled bookings")));
        assertTrue(events.stream().anyMatch(e->e.cards().size()==1));verifyNoInteractions(ai);
        assertFalse(conversations.memory.get(state.id).toString().contains("1234567890"));
    }
    @Test void shortcutsAreReadOnlyAndSupportTheRequestedPhrases(){
        assertEquals("ALL",AccountQuestion.parse("Give my booked tickets").scope());
        assertEquals("COUNT",AccountQuestion.parse("How many bookings I have ?").scope());
        assertEquals("NEXT",AccountQuestion.parse("When is my next journey?").scope());
        assertEquals(1,AccountQuestion.parse("show my cancelled bookings page 2").page());
        assertNull(AccountQuestion.parse("Cancel my bookings"));assertNull(AccountQuestion.parse("Show Bob's bookings"));
        assertNull(AccountQuestion.parse("Show my bookings for tomorrow"));
    }
    @Test void quotaIsNotBlamedForTimeoutOrOtherFailures()throws Exception{
        var knowledge=new KnowledgeBase(mock(AiModels.class));
        assertTrue(knowledge.fallback("hello","TIMEOUT").contains("did not respond in time"));
        assertFalse(knowledge.fallback("hello","TIMEOUT").contains("quota"));
        assertTrue(knowledge.fallback("hello","RATE_LIMITED").contains("does not by itself mean your daily allowance is exhausted"));
    }
}
