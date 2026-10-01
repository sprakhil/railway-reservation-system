package com.railway.chatbotservice.api;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
public final class ChatTypes {
    private ChatTypes(){}
    public record Ask(@NotBlank @Size(max=2000) String message) {}
    public record AnswerChunk(String text) {}
    public record Source(String id,String title,String excerpt) {}
    public record Card(String kind,String title,String pnr,String trainNumber,String fromStation,String toStation,
        String dateOfJourney,String seatClass,Integer passengers,String status,Double fare,Integer availableSeats,
        String departureAt,String refundStatus,Double refundAmount) {}
    public record UiMessage(String role,String text,List<Card> cards,List<Source> sources,Instant at) {}
    public record Conversation(String id,Instant expiresAt,List<UiMessage> messages) {}
    public record Event(String type,String text,List<Card> cards,List<Source> sources) {
        public static Event text(String type,String text){return new Event(type,text,List.of(),List.of());}
        public static Event cards(List<Card> cards){return new Event("cards","",List.copyOf(cards),List.of());}
        public static Event sources(List<Source> sources){return new Event("sources","",List.of(),List.copyOf(sources));}
    }
}
