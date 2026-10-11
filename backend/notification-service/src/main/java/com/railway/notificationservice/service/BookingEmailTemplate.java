package com.railway.notificationservice.service;

import com.railway.notificationservice.dto.NotificationEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.math.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class BookingEmailTemplate {
    public record Content(String subject,String plain,String html) {}
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'IST'",Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"));
    private static String text(JsonNode node,String key) { return node.path(key).isMissingNode()||node.path(key).isNull()?"":node.path(key).asText(); }
    private static String money(JsonNode node,String key) { String v=text(node,key); return v.isBlank()?"Not recorded":"INR "+new BigDecimal(v).setScale(2,RoundingMode.HALF_UP); }
    private static String date(String value){try{return LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd MMM yyyy",Locale.ENGLISH));}catch(Exception ex){return Objects.toString(value,"Not recorded");}}
    private static String time(String value) { if(value.isBlank())return "Not recorded"; try{return TIME.format(Instant.parse(value));}catch(Exception ex){return value;} }
    private static String row(String key,String value) {return "<tr><td style='padding:10px 12px;border-bottom:1px solid #e2e9df;color:#61766a;font-size:13px;overflow-wrap:anywhere'>"+RailwizeMail.escape(key)+"</td><td style='padding:10px 12px;border-bottom:1px solid #e2e9df;font-size:14px;font-weight:bold;overflow-wrap:anywhere'>"+RailwizeMail.escape(value)+"</td></tr>";}
    private static boolean eventNeedsTicket(String type){return "PAYMENT_RECEIVED".equals(type)||"PROMOTED".equals(type);}
    public static Content render(NotificationEvent e,String frontend,String timeline,Instant now) {
        JsonNode b=new JsonMapper().readTree(e.getBookingJson()==null?"{}":e.getBookingJson());
        String type=Objects.toString(e.getEventType(),"BOOKING_UPDATE");
        boolean waiting="WAITING".equals(text(b,"status"));
        String title=switch(type){
            case "TRAIN_CANCELLED" -> "Your train has been cancelled.";
            case "PAYMENT_RECEIVED" -> waiting?"You’re on the waitlist.":"Your journey is confirmed.";
            case "PROMOTED" -> "Good news. Your seats are confirmed.";
            case "PAYMENT_FAILED" -> "Your payment did not go through.";
            case "PAYMENT_INCOMPLETE" -> "Your booking hold has expired.";
            case "CANCELLED" -> "Your booking has been cancelled.";
            case "WAITLIST_EXPIRED" -> "Your waitlisted booking has been cancelled.";
            case "REFUND_INITIATED" -> "Your refund has been requested.";
            case "REFUND_COMPLETED" -> "Your test refund is processed.";
            case "REFUND_DELAYED" -> "Your refund needs attention.";
            default -> "An update on your journey.";
        };
        String intro=switch(type){
            case "PAYMENT_RECEIVED" -> waiting?"Your test payment was received. Your whole booking is waitlisted; we’ll email you if seats become available.":"Your test payment was verified and your seats are reserved. Your PDF ticket is attached.";
            case "PROMOTED" -> "Your whole booking has moved from the waitlist to confirmed. Your updated PDF ticket is attached.";
            case "PAYMENT_FAILED" -> "Razorpay reported a failed payment attempt. Check the current booking status before retrying; your original hold deadline still applies.";
            case "PAYMENT_INCOMPLETE" -> "Payment was not confirmed before the two-minute hold ended. Your seats have been released. Any late captured test payment is reconciled and refunded.";
            case "CANCELLED","WAITLIST_EXPIRED" -> "All passengers listed below have been cancelled. Your cancellation fee and eligible refund are shown here.";
            case "REFUND_INITIATED" -> "Your eligible refund has entered processing. We’ll send a separate confirmation after the provider marks it processed.";
            case "REFUND_COMPLETED" -> "Razorpay has marked your test-mode refund as processed. No real money was charged or credited to a bank account or payment method.";
            case "REFUND_DELAYED" -> "The provider has not completed the refund successfully. Your booking page will show further updates; this is not a refund completion receipt.";
            default -> Objects.toString(e.getMessage(),"Open your booking for the latest information.");
        };
        if(eventNeedsTicket(type) && (e.getTicketPdf()==null || e.getTicketPdf().length==0))
            intro=intro.replace("Your PDF ticket is attached.","Open your booking to download your ticket.").replace("Your updated PDF ticket is attached.","Open your booking to download the updated ticket.");
        StringBuilder plain=new StringBuilder("Hello "+Objects.toString(e.getUsername(),"traveller")+",\n\n"+intro+"\n\n");
        StringBuilder body=new StringBuilder("<table role='presentation' width='100%' cellspacing='0' cellpadding='0' style='background:#f6f8f1;border-radius:10px'>");
        var details=new LinkedHashMap<String,String>();
        details.put("PNR",e.getPnr());
        details.put("Train",Objects.toString(e.getTrainNumber(),"")+(text(b,"trainName").isBlank()?"":" · "+text(b,"trainName")));
        details.put("Journey date",date(e.getDateOfJourney()));
        details.put("Boarding station",text(b,"fromStation"));details.put("Destination",text(b,"toStation"));
        details.put("Departure",time(text(b,"departureAt")));details.put("Travel class",text(b,"seatClass").replace("CLASS_",""));
        details.put("Booking status",text(b,"status"));details.put("Booking fare",money(b,"totalFare"));
        if(waiting){details.put("Waitlist position",text(b,"waitlistPosition"));details.put("Confirmation deadline",time(text(b,"waitlistDeadline")));}
        if(!text(b,"operatorCancellationReason").isBlank()) {
            details.put("Cancelled by", "Train operator"); details.put("Reason",text(b,"operatorCancellationReason"));
            details.put("Original run date",text(b,"runDate"));
        }
        boolean cancellation=Set.of("TRAIN_CANCELLED","CANCELLED","WAITLIST_EXPIRED","REFUND_INITIATED","REFUND_COMPLETED","REFUND_DELAYED").contains(type);
        if(cancellation){details.put("Cancellation fee",money(b,"cancellationFee"));details.put("Refund amount",money(b,"refundAmount"));details.put("Refund status",text(b,"refundStatus"));}
        boolean retry=false;
        if(type.equals("PAYMENT_FAILED")){
            String deadline=text(b,"holdExpiresAt");
            try{retry=Instant.parse(deadline).isAfter(now);}catch(Exception ignored){}
            details.put("Payment deadline",time(deadline));
        }
        for(var entry:details.entrySet())if(entry.getValue()!=null&&!entry.getValue().isBlank()){body.append(row(entry.getKey(),entry.getValue()));plain.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");}
        body.append("</table>");
        if(b.path("passengers").isArray()){
            body.append("<h2 style='font-size:18px;margin:26px 0 12px'>").append(cancellation?"Passengers on this booking":"Your passengers").append("</h2><table width='100%' cellspacing='0' cellpadding='0'><tr><th align='left' style='padding:10px 12px;background:#eef3e9'>Passenger</th><th align='left' style='padding:10px 12px;background:#eef3e9'>Coach / berth</th></tr>");
            for(JsonNode p:b.path("passengers")){
                String seat=text(p.path("seatDetails"),"description");
                if(seat.isBlank())seat=text(p,"seatNumber");if(seat.isBlank())seat="Not allocated";
                body.append(row(text(p,"name"),seat));plain.append("Passenger: ").append(text(p,"name")).append(" · Coach / berth: ").append(seat).append("\n");
            }body.append("</table>");
        }
        String note="This email records the booking at the time of this update. Open your booking for its latest status. Test mode · No real money is charged or refunded. All departure times are India Standard Time.";
        if(cancellation&&!type.equals("REFUND_COMPLETED"))note+="NONE".equals(text(b,"refundStatus"))?" No refund is due for this booking.":" "+timeline;
        if(waiting)note+=" A paid waitlisted booking is automatically cancelled with a full test refund if it is not confirmed by the cutoff.";
        if(type.equals("PAYMENT_FAILED")&&!retry)note+=" The original hold deadline has passed. Open the booking to check its current status; make a new booking if it has expired.";
        String url=frontend.replaceAll("/+$","")+"/bookings/"+URLEncoder.encode(e.getPnr(),StandardCharsets.UTF_8);
        String action=retry?"Check status & retry payment":"View booking";
        plain.append("\n").append(note).append("\n\n").append(action).append(": ").append(url).append("\n\nRailwize");
        return new Content("Railwize | "+title+" | PNR "+e.getPnr(),plain.toString(),RailwizeMail.page(title,intro,body.toString(),url,action,note));
    }
}
