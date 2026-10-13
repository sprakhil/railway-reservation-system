package com.railway.authservice.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Account messages share the same email-client-friendly shell as booking updates. */
public final class AccountEmailTemplate {
    private AccountEmailTemplate() {}
    public record Content(String subject,String plain,String html) {}
    public static Content otp(String otp,boolean reset,String frontend) {
        String title=reset?"A fresh start for your account.":"One small step. You’re almost aboard.";
        String intro=reset?"We received a request to reset your Railwize password. Enter the code below on the password-reset page to choose a new password.":"Thanks for joining Railwize. Enter the code below on the email-verification page to activate your account.";
        String purpose=reset?"PASSWORD RESET CODE":"EMAIL VERIFICATION CODE";
        String instructions=reset?"Enter your registered email address and this code, then choose a new password. Once your password is changed, sign in again on your devices.":"Return to registration, enter your email address and this code, then sign in to start planning your journey.";
        String footer="This code expires in 5 minutes and can be used once for this purpose. Never share it with anyone, including someone claiming to be Railwize support. "
            +(reset?"If you did not request this reset, ignore this email. Your password has not been changed.":"If you did not create this account, ignore this email. Your email address will not be verified by opening this message.");
        String content="<div style='padding:24px 16px;background:#e9eef8;border:1px solid #d1daf0;border-radius:12px;text-align:center'>"
            +"<p style='margin:0 0 14px;color:#515f81;font-size:11px;font-weight:bold;letter-spacing:1.6px'>"+purpose+"</p>"
            +"<div style='font-family:Consolas,monospace;font-size:34px;font-weight:bold;letter-spacing:6px;color:#152241;white-space:nowrap'>"+RailwizeMail.escape(otp)+"</div>"
            +"<p style='font-size:12px;color:#536284;margin:14px 0 0'>Valid for 5 minutes &middot; Single use</p></div>"
            +"<h2 style='font-size:18px;margin:25px 0 10px'>What happens next</h2><p style='font-size:14px;line-height:1.8;color:#475472;margin:0'>"+RailwizeMail.escape(instructions)+"</p>";
        String url=frontend.replaceAll("/+$","")+(reset?"/auth/reset":"/auth/verify");
        return new Content(reset?"Railwize | Your password-reset code":"Railwize | Verify your email address",
            intro+"\n\n"+purpose+": "+otp+"\n\n"+instructions+"\n\n"+footer+"\n\nOpen Railwize: "+url+"\n\nRailwize",
            RailwizeMail.page(title,intro,content,url,reset?"Reset my password":"Verify my email",footer));
    }
    public static Content invitation(String address,String url,Instant expiresAt) {
        String expiry=DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'IST'",Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata")).format(expiresAt);
        String intro="You have been invited to join the Railwize administrator workspace.";
        String instructions="Register and verify the invited email address, or sign in with its existing verified account. Then open the invitation link to review and accept your administrator access. After acceptance, sign in again to use your new role.";
        String footer="This invitation is single-use and expires within 24 hours ("+expiry+"). It is valid only for the invited verified email account. Do not share or forward this link. If you were not expecting an administrator invitation, ignore this email.";
        String content="<div style='padding:20px;background:#e9eef8;border-radius:12px'>"
            +"<p style='font-size:11px;letter-spacing:1.5px;color:#536284;margin:0 0 8px'>ADMINISTRATOR INVITATION</p>"
            +"<p style='font-size:16px;font-weight:bold;margin:0 0 14px;overflow-wrap:anywhere'>"+RailwizeMail.escape(address)+"</p>"
            +"<p style='font-size:13px;line-height:1.7;margin:0'>Accept before <strong>"+RailwizeMail.escape(expiry)+"</strong></p></div>"
            +"<h2 style='font-size:18px;margin:24px 0 10px'>Your workspace</h2><p style='font-size:14px;line-height:1.8;color:#475472;margin:0'>Manage stations, trains, fares and published timetables. View authorized booking details and manage administrator invitations.</p>"
            +"<h2 style='font-size:18px;margin:24px 0 10px'>How to get started</h2><p style='font-size:14px;line-height:1.8;color:#475472;margin:0'>"+RailwizeMail.escape(instructions)+"</p>";
        return new Content("Railwize | Your administrator invitation",intro+"\n\nInvited email: "+address+"\nExpires: "+expiry+"\n\n"+instructions+"\n\nReview invitation:\n"+url+"\n\n"+footer+"\n\nRailwize",
            RailwizeMail.page("A place in the workspace.",intro,content,url,"Review invitation",footer));
    }
}
