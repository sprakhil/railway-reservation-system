package com.railway.chatbotservice.service;

import com.google.genai.Client;
import com.google.genai.types.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.google.genai.*;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.retry.*;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import java.util.*;

@Component
public class AiModels {
    private final Client client;
    private final ChatClient chat;
    private final EmbeddingModel embeddings;
    public AiModels(@Value("${chat.gemini-key:}") String key,@Value("${chat.model:gemini-3.5-flash-lite}") String model,
        @Value("${chat.embedding-model:gemini-embedding-001}") String embeddingModel,Guardrails guard) {
        if(key.isBlank()){client=null;chat=null;embeddings=null;return;}
        client=Client.builder().apiKey(key).vertexAI(false).httpOptions(HttpOptions.builder().timeout(20000)
            .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build();
        var noRetry=new RetryTemplate(RetryPolicy.withMaxRetries(0));
        var llm=GoogleGenAiChatModel.builder().genAiClient(client).retryTemplate(noRetry)
            .options(GoogleGenAiChatOptions.builder().model(model).temperature(0.2).maxOutputTokens(2048).build()).build();
        chat=ChatClient.builder(llm).defaultAdvisors(org.springframework.ai.chat.client.advisor.ToolCallingAdvisor.builder().build(),new GuardrailAdvisor(guard)).build();
        var delegate=new GoogleGenAiTextEmbeddingModel(GoogleGenAiEmbeddingConnectionDetails.builder().genAiClient(client).build(),
            GoogleGenAiTextEmbeddingOptions.builder().model(embeddingModel).dimensions(768)
                .taskType(GoogleGenAiTextEmbeddingOptions.TaskType.SEMANTIC_SIMILARITY).build(),noRetry);
        embeddings=new MemoizedEmbeddings(delegate);
    }
    public boolean available(){return chat!=null;}
    public ChatClient chat(){return Objects.requireNonNull(chat,"Gemini is not configured");}
    public EmbeddingModel embeddings(){return Objects.requireNonNull(embeddings,"Gemini is not configured");}
    @PreDestroy public void close(){if(client!=null)client.close();}
    // Reuse one query embedding for both semantic-cache lookup and SimpleVectorStore search.
    static class MemoizedEmbeddings implements EmbeddingModel {
        private final EmbeddingModel delegate;
        private record Entry(float[] vector,long expires){}
        private final Map<String,Entry> cache=new LinkedHashMap<>();
        MemoizedEmbeddings(EmbeddingModel delegate){this.delegate=delegate;}
        public EmbeddingResponse call(EmbeddingRequest request){return delegate.call(request);}
        public float[] embed(Document document){return delegate.embed(document);}
        public synchronized float[] embed(String text){var found=cache.get(text);if(found!=null&&found.expires>System.currentTimeMillis())return found.vector.clone();float[] vector=delegate.embed(text);if(cache.size()>=256)cache.remove(cache.keySet().iterator().next());cache.put(text,new Entry(vector.clone(),System.currentTimeMillis()+120000));return vector;}
    }
}
