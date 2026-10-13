package com.railway.notificationservice.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.MimeMessageHelper;
import java.nio.charset.StandardCharsets;

/** Table layout and inline CSS work in email clients without remote images or scripts. */
public final class RailwizeMail {
    private RailwizeMail() {}
    public static String escape(String value) {
        return value==null ? "" : value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
    public static String page(String title,String intro,String content,String url,String action,String footer) {
        return "<!doctype html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
            +"<body style='margin:0;background:#f3f6fc;color:#152442;font-family:Arial,Helvetica,sans-serif'>"
            +"<div style='display:none;max-height:0;overflow:hidden'>"+escape(intro)+"</div>"
            +"<table role='presentation' width='100%' cellspacing='0' cellpadding='0' style='background:#f3f6fc'><tr><td align='center' style='padding:28px 12px'>"
            +"<table role='presentation' width='600' cellspacing='0' cellpadding='0' style='width:100%;max-width:600px;background:#ffffff;border:1px solid #dde4f0;border-radius:16px;overflow:hidden'>"
            +"<tr><td style='padding:28px 30px;background:#0b1630;border-bottom:4px solid #61dcf2'>"
            +"<img src='cid:railwize-logo' alt='Railwize' width='200' height='50' style='display:block;border:0'><p style='color:#c8d3ed;font-size:11px;letter-spacing:2px;margin:16px 0 0'>EVERY JOURNEY. MORE POSSIBILITIES.</p></td></tr>"
            +"<tr><td style='padding:30px'><h1 style='font-size:27px;line-height:1.25;margin:0 0 16px;color:#0b1630'>"+escape(title)+"</h1>"
            +"<p style='font-size:15px;line-height:1.7;margin:0 0 24px;color:#58677f'>"+escape(intro)+"</p>"+content
            +(url==null?"":"<table role='presentation' cellspacing='0' cellpadding='0' style='margin-top:26px'><tr><td style='background:#2459e0;border-radius:9px;padding:15px 24px'><a href='"+escape(url)+"' style='color:#ffffff;text-decoration:none;font-weight:bold;font-size:14px'>"+escape(action)+" &rarr;</a></td></tr></table>")
            +"<p style='font-size:12px;line-height:1.7;color:#58688d;margin:24px 0 0'>"+escape(footer)+"</p></td></tr>"
            +"<tr><td style='padding:20px 30px;background:#edf3ff;color:#58677f;font-size:12px;line-height:1.7'>One search. More ways to go.<br><strong>Railwize</strong> &middot; Your journey, all in one place.</td></tr></table></td></tr></table></body></html>";
    }
    public static void logo(MimeMessageHelper helper) throws jakarta.mail.MessagingException {
        helper.addInline("railwize-logo",new ClassPathResource("mail/railwize-logo.png"),"image/png");
    }
}
