package com.railway.paymentservice.exception;
import jakarta.servlet.http.*; import java.io.IOException; import java.time.Instant; import java.util.*; import org.springframework.http.HttpStatus; import tools.jackson.databind.json.JsonMapper;
public final class ApiErrors {
 private static final JsonMapper JSON=JsonMapper.builder().build(); private ApiErrors(){}
 public static String requestId(HttpServletRequest req){Object id=req.getAttribute("requestId"); if(id==null){id=UUID.randomUUID().toString();req.setAttribute("requestId",id);} return id.toString();}
 public static Map<String,Object> body(HttpServletRequest req,int status,String message,Map<String,String> fields) {
  HttpStatus known=HttpStatus.resolve(status); String code=known==null?"REQUEST_FAILED":known.name();
  return Map.of("code",code,"status",status,"message",message==null?code:message,"fieldErrors",fields,"timestamp",Instant.now().toString(),"requestId",requestId(req));
 }
 public static void write(HttpServletRequest req,HttpServletResponse res,int status,String message)throws IOException {
  if(res.isCommitted()) return; res.resetBuffer();res.setStatus(status);res.setContentType("application/json");res.setCharacterEncoding("UTF-8");res.setHeader("X-Request-ID",requestId(req));
  res.getWriter().write(JSON.writeValueAsString(body(req,status,message,Map.of())));
 }
}
