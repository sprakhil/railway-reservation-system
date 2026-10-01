package com.railway.chatbotservice.service;
import com.railway.chatbotservice.api.*;
import com.railway.chatbotservice.api.ChatTypes.*;
import org.springframework.ai.chat.memory.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class Conversations {
    public final ChatMemory memory=MessageWindowChatMemory.builder().chatMemoryRepository(new InMemoryChatMemoryRepository()).maxMessages(20).build();
    private final Map<String,State> entries=new ConcurrentHashMap<>();
    public static final Duration TTL=Duration.ofHours(24);
    public static class State {
        public final String id=UUID.randomUUID().toString(),owner;
        public final AtomicBoolean busy=new AtomicBoolean();
        private volatile Instant expiresAt=Instant.now().plus(TTL);
        private final Deque<UiMessage> messages=new ArrayDeque<>();
        private final Map<String,String> bookingReferences=new LinkedHashMap<>();
        public State(String owner){this.owner=owner;}
        public synchronized String alias(String pnr){for(var e:bookingReferences.entrySet())if(e.getValue().equals(pnr))return e.getKey();if(bookingReferences.size()>=20)throw new ChatException(400,"Start a new conversation to look up more bookings.");String alias="booking-"+(bookingReferences.size()+1);bookingReferences.put(alias,pnr);return alias;}
        public synchronized String pnr(String alias){return bookingReferences.get(alias);}
        public synchronized Conversation view(){return new Conversation(id,expiresAt,List.copyOf(messages));}
        synchronized void append(UiMessage message){messages.add(message);while(messages.size()>20)messages.removeFirst();expiresAt=Instant.now().plus(TTL);}
    }
    public synchronized State create(String owner){expire();if(entries.values().stream().filter(e->e.owner.equals(owner)).count()>=5)throw new ChatException(409,"Clear an older conversation before starting another.");if(entries.size()>=500)throw new ChatException(503,"Chat is busy. Please try again later.");State state=new State(owner);entries.put(state.id,state);return state;}
    public State get(String owner,String id){State s=entries.get(id);if(s==null||!s.owner.equals(owner)||s.expiresAt.isBefore(Instant.now()))throw new ChatException(404,"Conversation expired or not found. Start a new conversation.");return s;}
    public void acquire(State s){if(!s.busy.compareAndSet(false,true))throw new ChatException(409,"Wait for the current reply or stop it before sending another message.");}
    public void release(State s){s.busy.set(false);}
    public void completed(State s,String input,String answer,List<Card> cards,List<Source> sources){
        if(!entries.containsKey(s.id))return;
        memory.add(s.id,List.of(new UserMessage(input),new AssistantMessage(answer)));
        s.append(new UiMessage("user",input,List.of(),List.of(),Instant.now()));s.append(new UiMessage("assistant",answer,List.copyOf(cards),List.copyOf(sources),Instant.now()));
    }
    public void clear(String owner,String id){State s=get(owner,id);if(s.busy.get())throw new ChatException(409,"Stop the current reply before clearing this conversation.");entries.remove(id);memory.clear(id);}
    @Scheduled(fixedDelay=60000) public void expire(){entries.values().removeIf(s->{if(!s.busy.get()&&s.expiresAt.isBefore(Instant.now())){memory.clear(s.id);return true;}return false;});}
}
