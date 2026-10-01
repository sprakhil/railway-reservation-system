package com.railway.chatbotservice.service;
import org.springframework.stereotype.Component;
import com.railway.chatbotservice.api.ChatTypes.Source;
import java.util.*;

@Component
public class SemanticCache {
    public record Hit(String answer,List<Source> sources){}
    private record Entry(String question,String qualifiers,float[] vector,Hit hit,long expires){}
    private final Deque<Entry> entries=new ArrayDeque<>();
    public boolean eligible(String q,boolean firstTurn){return firstTurn&&q.length()<350&&q.matches("(?is).*(policy|refund|cancellation|waitlist|\\bwl\\b|\\bcnf\\b|\\brac\\b|hold|advance.booking|passenger.limit).*")&&!q.matches("(?is).*(\\b(my|mine|our|pnr|booking-\\d+|train|tomorrow|today|not|never)\\b|\\d|\\[|@).*");}
    private String qualifiers(String q){var values=new TreeSet<String>();for(String token:q.toLowerCase(Locale.ROOT).split("\\W+"))if(Set.of("paid","unpaid","confirmed","waitlisted","wl","cnf","before","after","cancel","cancelled","deadline","hold","refund","cancellation","rac").contains(token))values.add(token);return values.toString();}
    public synchronized Hit find(String question,float[] vector){long now=System.currentTimeMillis();entries.removeIf(e->e.expires<now);String qualifiers=qualifiers(question);for(var e:entries)if(e.qualifiers.equals(qualifiers)&&(e.question.equalsIgnoreCase(question)||vector!=null&&cosine(vector,e.vector)>=0.96))return e.hit;return null;}
    public synchronized void put(String question,float[] vector,String answer,List<Source> sources){if(sources.isEmpty()||vector==null)return;while(entries.size()>=128)entries.removeFirst();entries.addLast(new Entry(question,qualifiers(question),vector.clone(),new Hit(answer,List.copyOf(sources)),System.currentTimeMillis()+1800000));}
    static double cosine(float[] a,float[] b){if(a.length!=b.length)return -1;double dot=0,aa=0,bb=0;for(int i=0;i<a.length;i++){dot+=a[i]*b[i];aa+=a[i]*a[i];bb+=b[i]*b[i];}return aa==0||bb==0?-1:dot/Math.sqrt(aa*bb);}
}
