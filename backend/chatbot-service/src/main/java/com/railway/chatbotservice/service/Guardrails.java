package com.railway.chatbotservice.service;
import com.railway.chatbotservice.api.ChatException;
import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.regex.*;

@Component
public class Guardrails {
    private static final Pattern INJECTION=Pattern.compile("(?is)(ignore|override|bypass|forget).{0,50}(instruction|system|security|permission|rule)|reveal.{0,30}(prompt|secret|api.?key|token)|\\b(system|developer)\\s*:\\s|<\\/?(system|instruction)>|act as.{0,30}(admin|root)|execute.{0,20}(sql|shell|command)");
    private static final Pattern EMAIL=Pattern.compile("(?i)[\\w.+%-]+@[\\w.-]+\\.[a-z]{2,}");
    private static final Pattern SECRET=Pattern.compile("(?i)(?:Bearer\\s+|AIza)[A-Za-z0-9_.=-]+|(?:password|api[_ -]?key|secret|otp|cvv|card[_ -]?number)\\s*[:=]\\s*\\S+");
    private static final Pattern LONG_NUMBER=Pattern.compile("(?<!\\w)(?:\\+?\\d[ -]?){9,19}(?!\\w)");
    private static final Pattern PNR=Pattern.compile("(?i)\\bPNR\\s*[:#-]?\\s*(\\d{10})\\b");
    public String prepare(String input,Conversations.State state){
        if(input==null||input.isBlank()||input.length()>2000)throw new ChatException(400,"Use a message between 1 and 2000 characters.");
        String value=Normalizer.normalize(input,Normalizer.Form.NFKC).replaceAll("[\\p{Cf}\\p{Cc}&&[^\\n\\t]]"," ");
        check(value);
        value=PNR.matcher(value).replaceAll(match->"booking reference "+state.alias(match.group(1)));
        return redact(value);
    }
    public void check(String text){if(INJECTION.matcher(text).find())throw new ChatException(400,"I can help with Railwize journeys and policies, but cannot override security rules or reveal private instructions.");}
    public String redact(String text){if(text==null)return "";return LONG_NUMBER.matcher(SECRET.matcher(EMAIL.matcher(text).replaceAll("[email removed]")).replaceAll("[secret removed]")).replaceAll("[private number removed]");}
    public String output(String text){if(text==null||text.length()>6000)throw new IllegalArgumentException("Invalid answer size");check(text);return redact(text).replaceAll("(?i)https?://\\S+","[external link removed]");}
}
