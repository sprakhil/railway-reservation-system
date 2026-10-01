package com.railway.pnrservice.config;
import com.railway.pnrservice.exception.ApiErrors; import jakarta.servlet.*; import jakarta.servlet.http.*; import java.io.IOException;
import org.springframework.stereotype.Component; import org.springframework.core.annotation.Order; import org.springframework.core.Ordered; import org.springframework.web.filter.OncePerRequestFilter;
@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiErrorFilter extends OncePerRequestFilter {
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
  String incoming=request.getHeader("X-Request-ID"); if(incoming!=null && incoming.matches("[A-Za-z0-9-]{8,80}")) request.setAttribute("requestId",incoming);
  response.setHeader("X-Request-ID",ApiErrors.requestId(request));
  HttpServletResponseWrapper wrapped=new HttpServletResponseWrapper(response){
   @Override public void sendError(int status)throws IOException{sendError(status,org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase());}
   @Override public void sendError(int status,String message)throws IOException{ApiErrors.write(request,this,status,status>=500?"A required service is temporarily unavailable":message);}
  };
  try{chain.doFilter(request,wrapped);}catch(Exception ex){if(response.isCommitted())throw ex;ApiErrors.write(request,response,500,"The service could not complete this request");}
 }
}
