package com.ddareungi.data.extended;
import java.math.BigDecimal;
import java.time.LocalDate;
public record UsageRow(String dataset,String resolution,LocalDate date,int hour,String number,String name,long count,BigDecimal minutes,BigDecimal meters) {
    public UsageRow {
        if(date==null || hour < -1 || hour>23 || count<0 || name==null || name.isBlank() || !number.matches("[0-9]+") ||
            minutes!=null&&minutes.signum()<0 || meters!=null&&meters.signum()<0) throw new IllegalArgumentException("INVALID_USAGE_ROW");
    }
    public String key(){return dataset+"|"+resolution+"|"+date+"|"+hour+"|"+number;}
    public UsageRow plus(UsageRow other){return new UsageRow(dataset,resolution,date,hour,number,name,Math.addExact(count,other.count),
        minutes==null||other.minutes==null?null:minutes.add(other.minutes),meters==null||other.meters==null?null:meters.add(other.meters));}
}
