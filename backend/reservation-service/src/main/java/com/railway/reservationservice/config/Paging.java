package com.railway.reservationservice.config;
import org.springframework.data.domain.*; import java.util.Set;
public final class Paging {
 private Paging(){}
 public static Pageable of(int page,int size,String sort,String direction,Set<String> fields,String id) {
  if(page<0 || page>100000 || size<1 || size>100 || !fields.contains(sort)) throw new IllegalArgumentException("Invalid pagination or sort field; size must be 1-100");
  if(!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction)) throw new IllegalArgumentException("Sort direction must be asc or desc");
  Sort order=Sort.by(Sort.Direction.fromString(direction),sort); if(!id.equals(sort)) order=order.and(Sort.by(id));
  return PageRequest.of(page,size,order);
 }
}
