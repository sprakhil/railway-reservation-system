package com.railway.userservice.service;
import com.railway.userservice.dto.*; import com.railway.userservice.entity.SavedPassenger; import com.railway.userservice.repository.SavedPassengerRepository; import com.railway.userservice.config.Paging;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.server.ResponseStatusException; import org.springframework.http.HttpStatus; import java.util.Set;
@Service @RequiredArgsConstructor
public class SavedPassengerService {
 private final SavedPassengerRepository passengers;
 @Transactional(readOnly=true) public PageResult<SavedPassenger> list(String owner,int page,int size){return PageResult.of(passengers.findByUsername(owner,Paging.of(page,size,"name","asc",Set.of("name"),"id")));}
 @Transactional public SavedPassenger save(String owner,Long id,SavedPassengerRequest req){
  SavedPassenger p=id==null?new SavedPassenger():owned(owner,id); p.setUsername(owner); p.setName(req.name().trim()); p.setAge(req.age()); p.setGender(req.gender()); p.setBerthPreference(req.berthPreference()); return passengers.save(p);
 }
 @Transactional public void delete(String owner,Long id){passengers.delete(owned(owner,id));}
 private SavedPassenger owned(String owner,Long id){return passengers.findByIdAndUsername(id,owner).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Saved passenger not found"));}
}
