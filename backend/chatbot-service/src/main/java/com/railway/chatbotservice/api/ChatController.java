package com.railway.chatbotservice.api;
import com.railway.chatbotservice.api.ChatTypes.*;
import com.railway.chatbotservice.config.SecurityConfig.Identity;
import com.railway.chatbotservice.service.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;
import java.util.Map;

@RestController @RequestMapping("/api/chat") @Tag(name="Railwize assistant",description="Signed-in, read-only assistance. In-memory conversations expire after 24 hours or service restart.")
public class ChatController {
    private final Conversations conversations;private final ChatService chat;
    public ChatController(Conversations conversations,ChatService chat){this.conversations=conversations;this.chat=chat;}
    @GetMapping("/status") public Map<String,Object> status(){return chat.status();}
    @PostMapping("/conversations") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Start a private conversation")
    public Conversation create(Authentication auth){return conversations.create(auth.getName()).view();}
    @GetMapping("/conversations/{id}") @Operation(summary="Read my recent conversation")
    public Conversation get(Authentication auth,@PathVariable String id){return conversations.get(auth.getName(),id).view();}
    @DeleteMapping("/conversations/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Clear my conversation and memory")
    public void clear(Authentication auth,@PathVariable String id){conversations.clear(auth.getName(),id);}
    @PostMapping(value="/conversations/{id}/messages",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary="Stream an answer and verified cards",description="POST with bearer token and {message}. SSE events: status, delta, cards, sources, fallback, done. Text is released as validated JSON paragraphs, not unchecked tokens. Stop by closing the connection. Do not automatically retry a partial stream.")
    public ResponseEntity<Flux<ServerSentEvent<Event>>> ask(Authentication auth,@PathVariable String id,@Valid @RequestBody Ask request){
        var stream=chat.ask((Identity)auth.getPrincipal(),id,request.message()).map(e->ServerSentEvent.builder(e).event(e.type()).build()).subscribeOn(Schedulers.boundedElastic());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering","no").body(stream);
    }
}
