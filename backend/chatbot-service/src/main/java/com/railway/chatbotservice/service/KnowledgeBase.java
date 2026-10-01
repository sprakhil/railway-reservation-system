package com.railway.chatbotservice.service;
import com.railway.chatbotservice.api.ChatTypes.Source;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class KnowledgeBase {
    private final AiModels models;private final List<Document> documents;private volatile SimpleVectorStore store;
    private final AtomicBoolean loading=new AtomicBoolean();
    public KnowledgeBase(AiModels models) throws java.io.IOException {
        this.models=models;
        String faq=new ClassPathResource("knowledge/railwize-faq.md").getContentAsString(StandardCharsets.UTF_8);
        var list=new ArrayList<Document>();int i=0;
        for(String section:faq.split("(?m)^## ")){if(section.isBlank()||section.startsWith("# "))continue;String title=section.lines().findFirst().orElse("Railwize policy");String id="faq-"+(++i);list.add(new Document(id,section.trim(),Map.of("title",title,"source","Railwize platform FAQ")));}
        documents=List.copyOf(list);
    }
    @EventListener(ApplicationReadyEvent.class) public void startup(){index();}
    @Scheduled(fixedDelay=60000,initialDelay=60000) public void index(){
        if(!models.available()||store!=null||!loading.compareAndSet(false,true))return;
        Mono.fromCallable(()->{var candidate=SimpleVectorStore.builder(models.embeddings()).build();candidate.add(documents);return candidate;})
            .subscribeOn(Schedulers.boundedElastic()).timeout(Duration.ofSeconds(45)).doFinally(s->loading.set(false))
            .subscribe(candidate->store=candidate,error->{}); // No request bodies, API keys or provider exception payloads in logs.
    }
    public boolean ready(){return store!=null;}
    public List<Source> find(String question){
        if(store!=null)try{return store.similaritySearch(SearchRequest.builder().query(question).topK(3).similarityThreshold(0.55).build()).stream()
            .map(d->new Source(d.getId(),String.valueOf(d.getMetadata().get("title")),d.getText())).toList();}catch(Exception unavailable){/* Embeddings must not disable an otherwise healthy chat model. */}
        var words=new HashSet<>(Arrays.asList(question.toLowerCase(Locale.ROOT).split("\\W+")));
        return documents.stream().filter(d->Arrays.stream(String.valueOf(d.getMetadata().get("title")).toLowerCase(Locale.ROOT).split("\\W+")).anyMatch(w->w.length()>2&&words.contains(w))).limit(3)
            .map(d->new Source(d.getId(),String.valueOf(d.getMetadata().get("title")),d.getText())).toList();
    }
    public String fallback(String question){return fallback(question,"PROVIDER_OR_RESPONSE_ERROR");}
    public String fallback(String question,String reason){
        var words=new HashSet<>(Arrays.asList(question.toLowerCase(Locale.ROOT).split("\\W+")));
        Document best=null;int score=0;
        for(var doc:documents){int current=0;for(String word:String.valueOf(doc.getMetadata().get("title")).toLowerCase(Locale.ROOT).split("\\W+"))if(word.length()>2&&words.contains(word))current++;if(current>score){best=doc;score=current;}}
        String message=switch(reason){
            case "RATE_LIMITED"->"The AI provider rate-limited this request. This can be a short-term request/token limit or a quota limit; it does not by itself mean your daily allowance is exhausted. Please try again later.";
            case "TIMEOUT"->"The AI provider did not respond in time. Please try again.";
            case "KEY_MISSING"->"AI replies are not configured. Account booking questions can still be answered directly.";
            case "API_KEY_REJECTED"->"The AI provider rejected the configured credentials. The administrator needs to check the API key.";
            case "MODEL_UNAVAILABLE"->"The configured AI model is unavailable. The administrator needs to check the model setting.";
            case "CIRCUIT_OPEN"->"AI replies are paused briefly after repeated failures. Please try again in about 30 seconds.";
            default->"I could not complete this AI reply. Please try again; this does not establish that your quota is exhausted.";
        };
        return message+" "+(best==null?"You can still use Find a train, My journeys and the Booking guide.":"Here is the platform FAQ guidance:\n\n"+best.getText())+"\n\nFor current availability or your booking status, open the normal booking screens.";
    }
}
