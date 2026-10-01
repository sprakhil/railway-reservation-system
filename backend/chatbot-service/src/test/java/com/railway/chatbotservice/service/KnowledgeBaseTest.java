package com.railway.chatbotservice.service;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.*;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class KnowledgeBaseTest {
    @Test void bundledFaqIsIndexedAndRetrievedFromRealSimpleVectorStore()throws Exception{
        var model=new EmbeddingModel(){
            float[] vector(String text){return text.toLowerCase().contains("cancellation fees")?new float[]{1,0}:new float[]{0,1};}
            public float[] embed(Document document){return vector(document.getText());}
            public EmbeddingResponse call(EmbeddingRequest request){var rows=new ArrayList<Embedding>();for(int i=0;i<request.getInstructions().size();i++)rows.add(new Embedding(vector(request.getInstructions().get(i)),i));return new EmbeddingResponse(rows);}
        };
        var ai=mock(AiModels.class);when(ai.available()).thenReturn(true);when(ai.embeddings()).thenReturn(model);
        var knowledge=new KnowledgeBase(ai);knowledge.startup();
        Mono.defer(()->knowledge.ready()?Mono.just(true):Mono.empty()).repeatWhenEmpty(50,repeats->repeats.delayElements(Duration.ofMillis(20))).block(Duration.ofSeconds(3));
        assertTrue(knowledge.ready());var sources=knowledge.find("cancellation fees");assertEquals(1,sources.size());assertTrue(sources.getFirst().excerpt().contains("20 percent"));assertTrue(sources.getFirst().title().contains("refund policy"));
    }
    @Test void queryEmbeddingIsReusedAndCallersCannotMutateCache(){
        var delegate=mock(EmbeddingModel.class);when(delegate.embed("question")).thenReturn(new float[]{1,2});
        var cache=new AiModels.MemoizedEmbeddings(delegate);cache.embed("question")[0]=999;
        assertArrayEquals(new float[]{1,2},cache.embed("question"));verify(delegate,times(1)).embed("question");
    }
}
