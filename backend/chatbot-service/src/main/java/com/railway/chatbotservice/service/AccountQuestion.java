package com.railway.chatbotservice.service;

import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative shortcuts for common account reads; all other questions go to the model. */
record AccountQuestion(String scope,int page) {
    static AccountQuestion parse(String raw){
        String q=raw.toLowerCase(Locale.ROOT).replaceAll("[?!.]","").strip().replaceAll("\\s+"," ");
        if(q.matches("(?:when is|what is|show(?: me)?|give(?: me)?|find) my next (?:journey|trip|train|booking)"))return new AccountQuestion("NEXT",0);
        if(q.matches("(?:how many (?:bookings|tickets)(?: do i have| i have| have i made)|(?:show(?: me)?|give(?: me)?) my booking (?:count|summary))"))return new AccountQuestion("COUNT",0);
        if(q.matches("which (?:tickets|bookings) (?:i have|have i) cancel(?:led|ed)"))return new AccountQuestion("CANCELLED",0);
        var match=Pattern.compile("(?:(?:show|give|list|find)(?: me)? )?my (?:(booked|cancelled|canceled|confirmed|waitlisted|waiting|pending|expired) )?(?:tickets|bookings|journeys)(?: page (\\d{1,5}))?").matcher(q);
        if(!match.matches())return null;
        int page=match.group(2)==null?0:Integer.parseInt(match.group(2))-1;
        if(page<0||page>10000)return null;
        String scope=switch(match.group(1)==null?"":match.group(1)){
            case "cancelled","canceled"->"CANCELLED";case "confirmed"->"CONFIRMED";
            case "waitlisted","waiting"->"WAITING";case "pending"->"PENDING_PAYMENT";case "expired"->"EXPIRED";default->"ALL";};
        return new AccountQuestion(scope,page);
    }
}
