package com.railway.authservice.service;

import com.railway.authservice.entity.AuthUser;
import com.railway.authservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class AdminUserService {
    private final AuthUserRepository users;
    private final RefreshTokenRepository sessions;
    public record UserView(Long id,String accountUsername,String username,String email,String role,String status,String provider,boolean verified){}
    public record UserPage(List<UserView> content,int page,int size,long totalElements,int totalPages){}
    private UserView view(AuthUser u){return new UserView(u.getId(),u.getUsername(),u.displayUsername(),u.getEmail(),u.getRole().name(),u.getStatus().name(),u.getProvider()==null?"LOCAL":u.getProvider().name(),u.isVerified()||u.isEmailVerified());}
    private void admin(String actor){
        var u=users.findByUsername(actor).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Administrator access required"));
        if(u.getRole()!=AuthUser.Role.ADMIN||u.getStatus()!=AuthUser.Status.ACTIVE)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Administrator access required");
    }
    private Specification<AuthUser> filter(String search,String status,String role){
        String term=search==null?"":search.strip().toLowerCase(Locale.ROOT);
        if(term.length()>100)throw new IllegalArgumentException("Search is too long");
        var wanted=status==null||status.isBlank()?null:AuthUser.Status.valueOf(status);
        var wantedRole=role==null||role.isBlank()?null:AuthUser.Role.valueOf(role);
        String like="%"+term.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        return (r,q,b)->{
            var p=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if(!term.isEmpty())p.add(b.or(b.like(b.lower(r.get("username")),like,'!'),b.like(b.lower(r.get("publicUsername")),like,'!'),b.like(b.lower(r.get("email")),like,'!')));
            if(wanted!=null)p.add(b.equal(r.get("status"),wanted));if(wantedRole!=null)p.add(b.equal(r.get("role"),wantedRole));
            return b.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
    @Transactional(readOnly=true)
    public UserPage list(String actor,String search,String status,String role,int page,int size,String sort,String direction){
        admin(actor);if(page<0||page>100000||size<1||size>100||!Set.of("id","username","email","status","role").contains(sort)||!Set.of("asc","desc").contains(direction))throw new IllegalArgumentException("Invalid pagination or sorting");
        var result=users.findAll(filter(search,status,role),PageRequest.of(page,size,Sort.by(Sort.Direction.fromString(direction),sort).and(Sort.by("id"))));
        return new UserPage(result.map(this::view).getContent(),page,size,result.getTotalElements(),result.getTotalPages());
    }
    @Transactional(readOnly=true) public Map<String,Long> stats(String actor){admin(actor);return Map.of("totalUsers",users.count(),"activeUsers",users.count(filter(null,"ACTIVE",null)),"blockedUsers",users.count(filter(null,"BLOCKED",null)),"admins",users.count(filter(null,null,"ADMIN")));}
    @Transactional
    public UserView status(String actor,String username,String status){
        admin(actor);
        if(!Set.of("ACTIVE","BLOCKED").contains(status==null?"":status))throw new IllegalArgumentException("Choose ACTIVE or BLOCKED");
        var u=users.findByUsernameForUpdate(username).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Account not found"));
        if(actor.equals(username)||u.getRole()==AuthUser.Role.ADMIN)throw new ResponseStatusException(HttpStatus.CONFLICT,"Administrator accounts cannot be blocked or changed here");
        if(u.getStatus()!=AuthUser.Status.ACTIVE&&u.getStatus()!=AuthUser.Status.BLOCKED)throw new ResponseStatusException(HttpStatus.CONFLICT,"Only active or blocked passenger accounts can be changed");
        var target=AuthUser.Status.valueOf(status);
        if(u.getStatus()!=target){var before=u.getStatus();u.setStatus(target);sessions.deleteByUser(u);log.info("Admin {} changed account id {} status from {} to {}",actor,u.getId(),before,target);}
        return view(u);
    }
    @Transactional(readOnly=true) public String csv(String actor,String search,String status,String role){
        admin(actor);var rows=users.findAll(filter(search,status,role),PageRequest.of(0,10001,Sort.by("id")));
        if(rows.getTotalElements()>10000)throw new ResponseStatusException(HttpStatus.CONFLICT,"Export exceeds 10000 users; narrow the filters");
        StringBuilder out=new StringBuilder("\uFEFFAccount key,Username,Email,Role,Status,Provider,Verified\r\n");
        for(var u:rows){var v=view(u);out.append(String.join(",",List.of(v.accountUsername(),v.username(),v.email(),v.role(),v.status(),v.provider(),Boolean.toString(v.verified())).stream().map(AdminUserService::cell).toList())).append("\r\n");}return out.toString();
    }
    static String cell(String value){String s=value==null?"":value;if(s.stripLeading().matches("(?s)^[=+@-].*")||s.startsWith("\t")||s.startsWith("\r")||s.startsWith("\n"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
}
