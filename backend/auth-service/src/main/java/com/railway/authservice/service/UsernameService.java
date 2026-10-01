package com.railway.authservice.service;

import com.railway.authservice.entity.AuthUser;
import com.railway.authservice.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Locale;

@Service @RequiredArgsConstructor
public class UsernameService {
    private final AuthUserRepository users;
    public record UsernameInfo(String username, boolean canChange, Instant changedAt) {}

    @Transactional(readOnly=true)
    public UsernameInfo current(String accountKey) {
        return info(users.findByUsername(accountKey).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found")));
    }

    @Transactional
    public UsernameInfo change(String accountKey, String requested) {
        String name=requested == null ? "" : requested.strip().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_]{4,20}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use 4-20 letters, numbers or underscores.");
        AuthUser user=users.findByUsernameForUpdate(accountKey)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (user.getStatus()!=AuthUser.Status.ACTIVE || !(user.isVerified()||user.isEmailVerified()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A verified active account is required.");
        // An identical retry is safe even after a response was lost.
        if (name.equalsIgnoreCase(user.displayUsername())) return info(user);
        if (user.getUsernameChangedAt()!=null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Your one username change has already been used.");
        if (users.existsByUsernameIgnoreCase(name)||users.existsByPublicUsernameIgnoreCase(name))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken. Choose another.");
        user.setPublicUsername(name);
        user.setUsernameChangedAt(Instant.now());
        users.saveAndFlush(user);
        return info(user);
    }

    private UsernameInfo info(AuthUser user) { return new UsernameInfo(user.displayUsername(),user.getUsernameChangedAt()==null,user.getUsernameChangedAt()); }
}
