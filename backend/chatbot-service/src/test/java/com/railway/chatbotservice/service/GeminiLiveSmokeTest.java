package com.railway.chatbotservice.service;
import com.railway.chatbotservice.api.ChatTypes.Event;
import com.railway.chatbotservice.config.SecurityConfig.Identity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in only. Sends a synthetic policy question, never booking/user data. */
@EnabledIfSystemProperty(named="railwize.live-gemini",matches="true")
class GeminiLiveSmokeTest {
    @Test void actualGeminiEmbeddingsAndSpringAiStreamingWork()throws Exception{
        var env=new HashMap<String,String>();for(String line:Files.readAllLines(Path.of("..",".env"))){int equals=line.indexOf('=');if(equals>0)env.put(line.substring(0,equals),line.substring(equals+1).strip());}
        var guard=new Guardrails();try(var models=new CloseableModels(new AiModels(env.get("GEMINI_API_KEY"),env.get("GEMINI_CHAT_MODEL"),env.get("GEMINI_EMBEDDING_MODEL"),guard))){
            var kb=new KnowledgeBase(models.ai);kb.startup();
            for(int i=0;i<100&&!kb.ready();i++)Thread.sleep(200);
            assertTrue(kb.ready(),"Real Gemini FAQ indexing did not complete");
            var memory=new Conversations();var state=memory.create("synthetic-smoke");var chat=new ChatService(models.ai,memory,guard,kb,new SemanticCache(),"http://localhost:1","http://localhost:1");
            var events=chat.ask(new Identity("synthetic-smoke","unused"),state.id,"How long is the payment hold?").collectList().block(Duration.ofSeconds(60));
            assertFalse(events.stream().anyMatch(e->e.type().equals("fallback")),"Safe diagnostics: "+chat.status());
            assertTrue(events.stream().anyMatch(e->e.type().equals("delta")));
            Files.writeString(Path.of("target","live-gemini-result.txt"),"FAQ indexed; live reply: "+events.stream().filter(e->e.type().equals("delta")).map(Event::text).reduce("",String::concat));
            // A local synthetic catalogue exercises real model function calling without user data.
            var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
            var calls=new java.util.concurrent.atomic.AtomicInteger();
            server.createContext("/api/reservations/search",exchange->{
                calls.incrementAndGet();
                byte[] body="[{\"trainNumber\":\"12345\",\"trainName\":\"Synthetic Coastal Express\",\"totalFare\":600,\"availableSeats\":5,\"suggestedAllocation\":\"CNF\"}]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
            });server.start();
            try{
                var liveTools=new ChatService(models.ai,memory,guard,kb,new SemanticCache(),"http://127.0.0.1:"+server.getAddress().getPort(),"http://localhost:1");
                var toolState=memory.create("synthetic-smoke");
                String date=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(2).toString();
                var toolEvents=liveTools.ask(new Identity("synthetic-smoke","unused"),toolState.id,"Find trains from station code CSMT to station code MAO on "+date+", class CLASS_3A, for 1 passenger.").collectList().block(Duration.ofSeconds(60));
                assertFalse(toolEvents.stream().anyMatch(e->e.type().equals("fallback")),"Tool diagnostics: "+liveTools.status());
                assertTrue(calls.get()>0,"Model did not call the search tool");
                assertTrue(toolEvents.stream().anyMatch(e->e.type().equals("cards")&&!e.cards().isEmpty()),"Verified train card missing");
                Files.writeString(Path.of("target","live-gemini-result.txt"),"\nReal model function calling returned a verified synthetic train card.",StandardOpenOption.APPEND);
            }finally{server.stop(0);}
        }
    }
    private record CloseableModels(AiModels ai) implements AutoCloseable {public void close(){ai.close();}}
}
