package com.railway.chatbotservice.service;
import com.railway.chatbotservice.api.ChatTypes.AnswerChunk;
import org.springframework.ai.converter.BeanOutputConverter;
import java.util.*;

/** Incrementally validates complete JSON records, never displaying raw/partial JSON or unfiltered tokens. */
public class AnswerDecoder {
    public static final BeanOutputConverter<AnswerChunk> CONVERTER=new BeanOutputConverter<>(AnswerChunk.class);
    private final Guardrails guard;
    private final StringBuilder pending=new StringBuilder();
    private int characters;
    public AnswerDecoder(Guardrails guard){this.guard=guard;}
    public List<String> accept(String tokens){
        if(tokens==null)return List.of();characters+=tokens.length();if(characters>12000)throw new IllegalArgumentException("Answer too long");
        pending.append(tokens);return drain(false);
    }
    public List<String> finish(){return drain(true);}
    private List<String> drain(boolean end){
        var chunks=new ArrayList<String>();
        while(!pending.isEmpty()){
            while(!pending.isEmpty()&&Character.isWhitespace(pending.charAt(0)))pending.deleteCharAt(0);
            if(pending.isEmpty())break;
            if(pending.charAt(0)=='`'){
                int newline=pending.indexOf("\n");
                if(newline<0){if(!end)return chunks;if(pending.toString().strip().equals("```")){pending.setLength(0);break;}throw new IllegalArgumentException("Incomplete answer fence");}
                String fence=pending.substring(0,newline).strip();if(!fence.equals("```")&&!fence.equals("```json"))throw new IllegalArgumentException("Invalid answer fence");pending.delete(0,newline+1);continue;
            }
            if(pending.charAt(0)!='{')throw new IllegalArgumentException("Expected an answer record");
            int depth=0,complete=-1;boolean quoted=false,escaped=false;
            for(int i=0;i<pending.length();i++){char c=pending.charAt(i);
                if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='\"')quoted=false;}
                else if(c=='\"')quoted=true;else if(c=='{')depth++;else if(c=='}'&&--depth==0){complete=i+1;break;}
            }
            if(complete<0){if(end)throw new IllegalArgumentException("Incomplete answer record");return chunks;}
            decode(pending.substring(0,complete),chunks);pending.delete(0,complete);
        }
        return chunks;
    }
    private void decode(String line,List<String> chunks){line=line.strip();if(line.isEmpty()||line.equals("```json")||line.equals("```"))return;
        var json=new tools.jackson.databind.json.JsonMapper().readTree(line);
        if(!json.isObject()||json.size()!=1||!json.path("text").isString())throw new IllegalArgumentException("Malformed answer record");
        var record=CONVERTER.convert(line);if(record==null||record.text()==null||record.text().isBlank()||record.text().length()>1500)throw new IllegalArgumentException("Malformed answer");
        chunks.add(guard.output(record.text()).strip()+"\n\n");
    }
}
