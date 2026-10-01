package com.railway.userservice.exception;
import org.springframework.web.bind.annotation.*; import org.springframework.web.bind.*; import org.springframework.http.*; import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.annotation.AnnotatedElementUtils; import jakarta.servlet.http.HttpServletRequest; import java.util.*; import lombok.extern.slf4j.Slf4j;
@RestControllerAdvice @Slf4j
public class GlobalExceptionHandler {
 @ExceptionHandler(Exception.class) public ResponseEntity<Map<String,Object>> handle(Exception ex,HttpServletRequest request) {
  int status=500;String message="The service could not complete this request";Map<String,String> fields=new LinkedHashMap<>();
  String name=ex.getClass().getSimpleName();
  var annotation=AnnotatedElementUtils.findMergedAnnotation(ex.getClass(),ResponseStatus.class);
  if(annotation!=null){status=annotation.code().value();message=ex.getMessage();}
  if(ex instanceof ResponseStatusException e){status=e.getStatusCode().value();message=e.getReason();}
  else if(ex instanceof MethodArgumentNotValidException e){status=400;message="Request validation failed";e.getBindingResult().getFieldErrors().forEach(f->fields.put(f.getField(),f.getDefaultMessage()==null?"Invalid value":f.getDefaultMessage()));}
  else if(ex instanceof IllegalArgumentException || ex instanceof ServletRequestBindingException || Set.of("MethodArgumentTypeMismatchException","HttpMessageNotReadableException","HandlerMethodValidationException","ConstraintViolationException").contains(name)){status=400;message="Missing or invalid request fields";}
  else if(name.equals("HttpRequestMethodNotSupportedException")){status=405;message="HTTP method not supported";}
  else if(name.equals("HttpMediaTypeNotSupportedException")){status=415;message="Unsupported request content type";}
  else if(name.equals("HttpMediaTypeNotAcceptableException")){status=406;message="Requested response format is unavailable";}
  else if(name.equals("NoResourceFoundException")||name.equals("NoHandlerFoundException")||name.equals("ResourceNotFoundException")){status=404;message="Resource not found";}
  else if(ex instanceof org.springframework.security.access.AccessDeniedException){status=403;message="You do not have permission for this operation";}
  else if(name.contains("Authentication")||name.equals("BadCredentialsException")){status=401;message="Authentication required";}
  else if(name.contains("IntegrityViolation")||name.contains("OptimisticLock")||name.contains("CannotAcquireLock")||name.equals("CatalogConflictException")){status=409;message="The request conflicts with current data; refresh and retry";}
  else if(name.equals("CatalogUnavailableException")||ex instanceof org.springframework.web.client.RestClientException||name.contains("FeignException")){status=503;message="A required service is temporarily unavailable";}

  if(status>=500)log.error("Request {} failed ({})",ApiErrors.requestId(request),ex.getClass().getName());
  return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).header("X-Request-ID",ApiErrors.requestId(request)).body(ApiErrors.body(request,status,message,fields));
 }
}
