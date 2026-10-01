package com.railway.reservationservice.service;

import com.railway.reservationservice.dto.SeatDetails;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Railwize's fixed display layouts. Never changes capacity, allocation order or stored seat IDs. */
public final class SeatLayouts {
    private SeatLayouts() {}
    private static final Pattern SEAT=Pattern.compile("([A-Z0-9_]+)-C([1-9][0-9]?)-([1-9][0-9]{0,2})");
    private static final List<String> THREE_TIER=List.of("LOWER","MIDDLE","UPPER","LOWER","MIDDLE","UPPER","SIDE_LOWER","SIDE_UPPER");
    private static final List<String> TWO_TIER=List.of("LOWER","UPPER","LOWER","UPPER","SIDE_LOWER","SIDE_UPPER");
    public static SeatDetails describe(String seatClass,String seatNumber){
        if(seatNumber==null||seatNumber.isBlank())return new SeatDetails(null,null,null,null,"Not assigned",null,null);
        var match=SEAT.matcher(seatNumber);
        if(!match.matches()||!normalize(match.group(1)).equals(normalize(seatClass)))return unknown(seatNumber);
        int coach=Integer.parseInt(match.group(2)),position=Integer.parseInt(match.group(3));
        if(coach>30||position>100)return unknown(seatNumber);
        String clazz=normalize(seatClass);
        String prefix=switch(clazz){
            case "SL"->"S";case "CLASS_3A"->"B";case "CLASS_2A"->"A";
            case "CLASS_1A"->"H";case "CLASS_3E"->"M";case "EC"->"E";
            case "CLASS_2S"->"D";case "UR"->"GS";default->null;
        };
        if(prefix==null)return unknown(seatNumber);
        String coachLabel=prefix+coach;
        List<String> pattern=switch(clazz){case "SL","CLASS_3A"->THREE_TIER;case "CLASS_2A"->TWO_TIER;default->List.of();};
        if(!pattern.isEmpty()){
            String type=pattern.get((position-1)%pattern.size()),label=label(type);
            return new SeatDetails(coachLabel,position,type,label,"Coach "+coachLabel+" · Berth "+position+" · "+label,
                pattern==THREE_TIER?"THREE_TIER_8_V1":"TWO_TIER_6_V1",(position-1)/pattern.size()+1);
        }
        if(Set.of("EC","CLASS_2S","UR").contains(clazz))return new SeatDetails(coachLabel,position,"SEAT","Seat",
            "Coach "+coachLabel+" · Seat "+position,"NUMBERED_SEATS_V1",null);
        // First AC cabin/coupe mixes and economy-coach variants need explicit layouts before assigning a berth type.
        return new SeatDetails(coachLabel,position,null,"Position not specified",
            "Coach "+coachLabel+" · Berth "+position+" · Position not specified",null,null);
    }
    private static SeatDetails unknown(String raw){return new SeatDetails(null,null,null,"Position not specified",raw,null,null);}
    private static String normalize(String value){
        if(value==null)return "";String name=value.toUpperCase(Locale.ROOT);
        return Set.of("1A","2A","3A","3E","2S").contains(name)?"CLASS_"+name:name;
    }
    private static String label(String type){return switch(type){case "LOWER"->"Lower berth";case "MIDDLE"->"Middle berth";case "UPPER"->"Upper berth";case "SIDE_LOWER"->"Side lower berth";case "SIDE_UPPER"->"Side upper berth";default->"Seat";};}
}
