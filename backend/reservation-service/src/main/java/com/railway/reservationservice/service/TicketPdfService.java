package com.railway.reservationservice.service;

import com.railway.reservationservice.dto.ReservationRes;
import com.railway.reservationservice.exception.BookingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@Service @RequiredArgsConstructor
public class TicketPdfService {
    private final BookingEngine bookings;
    @Value("${booking.pdf-font:}") private String fontPath;
    private static final Color GREEN=new Color(22,64,51), INK=new Color(30,49,43), MUTED=new Color(91,111,101),
        PALE=new Color(239,246,240), PAPER=new Color(251,250,246), LINE=new Color(220,229,220), ORANGE=new Color(224,128,68);
    private static final ZoneId INDIA=ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd MMM yyyy",Locale.ENGLISH);
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'IST'",Locale.ENGLISH).withZone(INDIA);

    public byte[] download(String pnr,String owner,boolean admin) {
        ReservationRes r=bookings.get(pnr,owner,admin);
        if(!"CAPTURED".equals(r.getPaymentStatus())) throw BookingException.conflict("A booking PDF is available after verified payment");
        return render(r);
    }

    public byte[] render(ReservationRes r) {
        try(PDDocument doc=new PDDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            PDType0Font regular=font(doc,"Lato-Regular.ttf",fontPath);
            PDType0Font bold=font(doc,"Lato-Bold.ttf",null);
            try(Canvas c=new Canvas(doc,regular,bold,r.getPnr())) {
                c.newPage(false);
                c.box(34,28,527,80,GREEN);
                c.train(50,47);
                c.text("railwize.",90,43,28,true,Color.WHITE);
                c.text("YOUR JOURNEY, ALL IN ONE PLACE",91,79,8,true,new Color(197,219,204));
                c.text("E-TICKET",455,47,11,true,Color.WHITE);
                c.text("BOOKING RECORD",455,65,7,false,new Color(197,219,204));
                c.box(34,108,527,4,ORANGE);
                c.box(34,127,527,60,PALE);
                c.text("BOOKING REFERENCE / PNR",50,139,8,true,MUTED);
                c.text(value(r.getPnr()),50,153,24,true,GREEN);
                String status=value(r.getStatus());
                Color statusColor="CONFIRMED".equals(status)?GREEN:"WAITING".equals(status)?new Color(145,91,20):new Color(148,57,46);
                float badgeWidth=Math.max(130,c.width(status,11,true)+26);
                c.box(545-badgeWidth,142,badgeWidth,29,statusColor);
                c.text(status,558-badgeWidth,150,11,true,Color.WHITE);
                c.box(34,203,527,102,PAPER);
                c.text("FROM",50,215,8,true,MUTED); c.text("TO",315,215,8,true,MUTED);
                c.text(value(r.getFromStation()),50,230,23,true,INK); c.text(value(r.getToStation()),315,230,23,true,INK);
                c.line(220,244,288,244,LINE); c.line(280,239,288,244,GREEN); c.line(280,249,288,244,GREEN);
                c.text("TRAIN "+value(r.getTrainNumber())+"  /  "+className(r.getSeatClass()),50,266,10,true,INK);
                c.text("JOURNEY DATE",315,267,7,true,MUTED);
                c.text(r.getDateOfJourney()==null?"Not available":DATE.format(r.getDateOfJourney()),315,279,11,true,INK);
                c.text("Boarding: "+(r.getDepartureAt()==null?"Check timetable":TIME.format(r.getDepartureAt())),50,284,9,false,MUTED);
                c.y=326;
                if("WAITING".equals(status)) {
                    String note="WAITLIST "+(r.getWaitlistPosition()==null?"": "POSITION "+r.getWaitlistPosition()+". ")+"Seats will be assigned after confirmation.";
                    if(r.getWaitlistDeadline()!=null)note+=" Cutoff: "+TIME.format(r.getWaitlistDeadline())+".";
                    c.paragraph(note,50,491,9,13,true,new Color(145,91,20)); c.y+=15;
                }
                c.passengerHeader();
                var people=r.getPassengers()==null?List.<com.railway.reservationservice.dto.PassengerDto>of():r.getPassengers();
                for(int i=0;i<people.size();i++) {
                    var p=people.get(i);
                    List<String> names=c.wrap(value(p.getName()),222,10,false);
                    var seat=SeatLayouts.describe(r.getSeatClass(),p.getSeatNumber());
                    List<String> seats=new ArrayList<>();
                    if(seat.coach()!=null){
                        seats.addAll(c.wrap(seat.coach()+" / "+("SEAT".equals(seat.berthType())?"Seat ":"Berth ")+seat.position(),109,9,true));
                        if(!"SEAT".equals(seat.berthType()))seats.addAll(c.wrap(seat.berthLabel(),109,9,true));
                    }else seats.addAll(c.wrap(seat.description(),109,9,true));
                    String state=p.getBookingStatus()==null?status:p.getBookingStatus();
                    float height=Math.max(36,Math.max(names.size()*13+12,seats.size()*11+19));
                    if(c.y+height>752){c.newPage(true);c.passengerHeader();}
                    c.box(34,c.y,527,height,i%2==0?PAPER:Color.WHITE);
                    c.text(String.format(Locale.ROOT,"%02d",i+1),48,c.y+10,10,true,MUTED);
                    c.lines(names,78,c.y+9,10,13,false,INK);
                    c.text(String.valueOf(p.getAge()),313,c.y+10,10,false,INK);
                    c.text(value(p.getGender()),352,c.y+10,9,false,INK);
                    c.lines(seats,435,c.y+8,9,11,true,INK);
                    c.text(state,435,c.y+8+seats.size()*11,7,false,MUTED);
                    c.y+=height; c.line(34,c.y,561,c.y,LINE);
                }
                c.y+=18;
                String snapshot="Keep your PNR handy. This ticket reflects the booking status when downloaded; check My journeys for subsequent changes.";
                String waitlist="WL bookings have no assigned seat until the entire booking is confirmed. An unconfirmed paid WL booking is refunded at the cutoff.";
                float summaryHeight=107+c.wrap(snapshot,527,8,false).size()*12
                    +("WAITING".equals(status)?5+c.wrap(waitlist,527,8,false).size()*12:0);
                if(c.y+summaryHeight>776)c.newPage(true);
                float fareTop=c.y;
                c.box(34,fareTop,527,91,PALE);
                c.text("FARE & PAYMENT",50,fareTop+13,10,true,GREEN);
                c.text("Fare per passenger: INR "+money(r.getFarePerPassenger()),50,fareTop+34,9,false,INK);
                c.text("Passengers: "+people.size()+"   |   Payment: "+value(r.getPaymentStatus()),50,fareTop+49,9,false,INK);
                c.text("Refund: "+value(r.getRefundStatus())+"   |   INR "+money(r.getRefundAmount()),50,fareTop+64,8,false,MUTED);
                c.text("TOTAL FARE",381,fareTop+15,8,true,MUTED);
                c.text("INR "+money(r.getTotalFare()),381,fareTop+31,18,true,GREEN);
                c.text("Payment mode: Razorpay TEST",381,fareTop+64,7,false,MUTED);
                c.y=fareTop+107;
                c.paragraph(snapshot,34,527,8,12,false,MUTED);
                if("WAITING".equals(status)) {c.y+=5;c.paragraph(waitlist,34,527,8,12,false,MUTED);}
                c.finish();
            }
            doc.getDocumentInformation().setTitle("Railwize ticket - "+r.getPnr());
            doc.getDocumentInformation().setAuthor("Railwize");
            doc.getDocumentInformation().setSubject("Booking status and passenger itinerary");
            doc.save(out);return out.toByteArray();
        }catch(IOException ex){throw new IllegalStateException("Could not generate booking PDF",ex);}
    }

    private PDType0Font font(PDDocument doc,String resource,String override)throws IOException {
        try(InputStream input=override==null||override.isBlank()?TicketPdfService.class.getResourceAsStream("/ticket/fonts/"+resource):Files.newInputStream(Path.of(override))) {
            if(input==null)throw new IOException("Bundled ticket font unavailable: "+resource);
            return PDType0Font.load(doc,input);
        }
    }
    private static String money(BigDecimal amount){return (amount==null?BigDecimal.ZERO:amount).setScale(2,RoundingMode.HALF_UP).toPlainString();}
    private static String value(String s){return s==null||s.isBlank()?"-":s;}
    private static String className(String s){return value(s).replace("CLASS_","");}

    /** Top-down page coordinates, measured wrapping, and repeated continuation headings. */
    private static final class Canvas implements AutoCloseable {
        final PDDocument doc;final PDType0Font regular,bold;final String pnr;PDPageContentStream stream;float y;
        Canvas(PDDocument doc,PDType0Font regular,PDType0Font bold,String pnr){this.doc=doc;this.regular=regular;this.bold=bold;this.pnr=pnr;}
        void newPage(boolean continued)throws IOException {
            if(stream!=null)stream.close();
            var page=new PDPage(PDRectangle.A4);doc.addPage(page);stream=new PDPageContentStream(doc,page);
            if(continued){box(34,28,527,48,GREEN);text("railwize. / JOURNEY DETAILS",50,42,15,true,Color.WHITE);text("PNR "+value(pnr),365,45,10,true,Color.WHITE);y=98;}
        }
        void passengerHeader()throws IOException {
            text("PASSENGERS",34,y,11,true,GREEN);y+=22;box(34,y,527,23,GREEN);
            text("NO.",48,y+7,8,true,Color.WHITE);text("PASSENGER NAME",78,y+7,8,true,Color.WHITE);
            text("AGE",309,y+7,8,true,Color.WHITE);text("GENDER",352,y+7,8,true,Color.WHITE);text("SEAT / STATUS",435,y+7,8,true,Color.WHITE);y+=23;
        }
        void finish()throws IOException {
            if(stream!=null){stream.close();stream=null;}
            String stamp="Downloaded "+TIME.format(Instant.now());
            for(int i=0;i<doc.getNumberOfPages();i++){
                stream=new PDPageContentStream(doc,doc.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true);
                line(34,792,561,792,LINE);text("RAILWIZE  /  "+stamp,34,803,7,false,MUTED);
                text("PAGE "+(i+1)+" OF "+doc.getNumberOfPages(),489,803,7,true,MUTED);stream.close();stream=null;
            }
        }
        void box(float x,float top,float w,float h,Color color)throws IOException {stream.setNonStrokingColor(color);stream.addRect(x,PDRectangle.A4.getHeight()-top-h,w,h);stream.fill();}
        void line(float x,float top,float x2,float top2,Color color)throws IOException {stream.setStrokingColor(color);stream.setLineWidth(0.8f);stream.moveTo(x,PDRectangle.A4.getHeight()-top);stream.lineTo(x2,PDRectangle.A4.getHeight()-top2);stream.stroke();}
        void train(float x,float top)throws IOException {box(x,top,26,31,Color.WHITE);box(x+4,top+5,18,11,GREEN);box(x+4,top+22,4,4,GREEN);box(x+18,top+22,4,4,GREEN);line(x+5,top+31,x+1,top+36,Color.WHITE);line(x+21,top+31,x+25,top+36,Color.WHITE);}
        String safe(String raw,boolean weight)throws IOException {
            var font=weight?bold:regular;StringBuilder b=new StringBuilder();
            for(int cp:raw.codePoints().toArray()){
                if(Character.isISOControl(cp)){b.append(' ');continue;}
                String ch=new String(Character.toChars(cp));
                try{font.encode(ch);b.append(ch);}catch(IllegalArgumentException ex){b.append("[U+").append(Integer.toHexString(cp).toUpperCase(Locale.ROOT)).append(']');}
            }return b.toString();
        }
        float width(String s,float size,boolean weight)throws IOException{return (weight?bold:regular).getStringWidth(safe(s,weight))*size/1000;}
        void text(String raw,float x,float top,float size,boolean weight,Color color)throws IOException {
            stream.beginText();stream.setFont(weight?bold:regular,size);stream.setNonStrokingColor(color);stream.newLineAtOffset(x,PDRectangle.A4.getHeight()-top-size);stream.showText(safe(raw,weight));stream.endText();
        }
        List<String> wrap(String raw,float width,float size,boolean weight)throws IOException {
            String remaining=safe(raw,weight).strip();List<String> lines=new ArrayList<>();
            while(!remaining.isEmpty()){
                int end=0,lastSpace=-1;
                while(end<remaining.length()){
                    int next=end+Character.charCount(remaining.codePointAt(end));
                    if(width(remaining.substring(0,next),size,weight)>width)break;
                    if(remaining.charAt(end)==' ')lastSpace=end;end=next;
                }
                if(end==0)end=Character.charCount(remaining.codePointAt(0));
                if(end<remaining.length()&&lastSpace>0)end=lastSpace;
                lines.add(remaining.substring(0,end).stripTrailing());remaining=remaining.substring(end).stripLeading();
            }
            if(lines.isEmpty())lines.add("");return lines;
        }
        void lines(List<String> lines,float x,float top,float size,float leading,boolean weight,Color color)throws IOException{for(String s:lines){text(s,x,top,size,weight,color);top+=leading;}}
        void paragraph(String s,float x,float width,float size,float leading,boolean weight,Color color)throws IOException{var lines=wrap(s,width,size,weight);lines(lines,x,y,size,leading,weight,color);y+=lines.size()*leading;}
        public void close()throws IOException{if(stream!=null)stream.close();}
    }
}
