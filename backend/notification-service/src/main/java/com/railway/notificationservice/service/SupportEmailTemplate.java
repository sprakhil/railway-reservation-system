package com.railway.notificationservice.service;

import com.railway.notificationservice.dto.NotificationEvent;
import java.util.Set;

public final class SupportEmailTemplate {
    private SupportEmailTemplate() {}
    public static BookingEmailTemplate.Content render(NotificationEvent event,String frontend) {
        String id=event.getSupportTicketId();
        if(id==null||!id.matches("SUP-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("Invalid support reference");
        boolean admin=Set.of("SUPPORT_NEW","SUPPORT_USER_REPLY").contains(event.getEventType());
        String title=switch(event.getEventType()) {
            case "SUPPORT_CREATED" -> "We’ve received your request.";
            case "SUPPORT_REPLY" -> "You have a reply from Railwize.";
            case "SUPPORT_NEW" -> "A new support request needs attention.";
            case "SUPPORT_USER_REPLY" -> "A passenger has replied.";
            default -> "Your support request has an update.";
        };
        String url=frontend.replaceAll("/+$","")+(admin?"/admin/support/":"/support/")+id;
        String intro=event.getMessage()==null?"Sign in to view the latest update.":event.getMessage();
        String content="<p style='font-size:13px;color:#52675e'>Request reference</p><p style='overflow-wrap:anywhere;font-weight:bold'>"+RailwizeMail.escape(id)+"</p>";
        if(event.getPnr()!=null)content+="<p>Booking PNR: "+RailwizeMail.escape(event.getPnr())+"</p>";
        String footer="Reply through your support conversation. Do not send passwords, OTPs or full card details. A support request does not cancel a ticket or change a refund automatically.";
        return new BookingEmailTemplate.Content("Railwize support | "+id,title+"\n\n"+intro+"\n\nRequest: "+id+"\n"+url+"\n\n"+footer,
            RailwizeMail.page(title,intro,content,url,admin?"Open support inbox":"View your request",footer));
    }
}
