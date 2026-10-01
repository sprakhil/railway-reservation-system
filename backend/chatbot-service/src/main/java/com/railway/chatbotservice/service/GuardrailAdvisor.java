package com.railway.chatbotservice.service;
import org.springframework.ai.chat.client.*;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.messages.UserMessage;
import java.util.*;

/** Defence in depth before provider calls. The streaming decoder separately gates every emitted chunk. */
public class GuardrailAdvisor implements BaseAdvisor {
    private final Guardrails guard;
    public GuardrailAdvisor(Guardrails guard){this.guard=guard;}
    // Run inside the tool loop, immediately before each model round.
    public int getOrder(){return Integer.MAX_VALUE-100;}
    public ChatClientRequest before(ChatClientRequest request,AdvisorChain chain){
        var rounds=request.context().get("railwize.rounds");
        if(rounds instanceof java.util.concurrent.atomic.AtomicInteger count&&count.incrementAndGet()>4)throw new IllegalStateException("Model round budget reached");
        var prompt=request.prompt().augmentUserMessage(user->{guard.check(user.getText());return new UserMessage(guard.redact(user.getText()));});
        return request.mutate().prompt(prompt).build();
    }
    public ChatClientResponse after(ChatClientResponse response,AdvisorChain chain){
        if(response.chatResponse()!=null&&response.chatResponse().getResult()!=null){
            String text=response.chatResponse().getResult().getOutput().getText();if(text!=null)guard.output(text);
        }
        return response;
    }
}
