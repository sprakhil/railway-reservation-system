package com.railway.authservice.service;

import com.railway.authservice.dto.*;
import com.railway.authservice.entity.AuthUser;
import com.railway.authservice.repository.*;
import com.railway.authservice.config.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.import=","spring.cloud.config.enabled=false","eureka.client.enabled=false","spring.cloud.compatibility-verifier.enabled=false","spring.datasource.url=jdbc:h2:mem:username-test;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.mail.host=localhost","jwt.secret=test-only-jwt-secret-with-at-least-32-bytes","jwt.expirationMs=900000","auth.bootstrap-admin.enabled=false"})
@AutoConfigureMockMvc
class UsernameIntegrationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean EmailService welcomeEmails;
    @Autowired AuthService auth; @Autowired UsernameService usernames; @Autowired AuthUserRepository users;
    @Autowired PasswordEncoder passwords; @Autowired JwtUtils jwt; @Autowired MockMvc mvc;

    private AuthUser account(String name) {
        return users.save(AuthUser.builder().username(name).email(name+"@example.test").password(passwords.encode("Password123!"))
            .provider(AuthUser.Provider.LOCAL).role(AuthUser.Role.PASSENGER).status(AuthUser.Status.ACTIVE).isVerified(true).emailVerified(true).build());
    }
    private AuthResponse login(String name) { var r=new LoginRequest();r.setIdentifier(name);r.setPassword("Password123!");return auth.login(r); }

    @Test void usernameChangePreservesSessionOwnerSupportsLoginAndIsLimitedToOnce() throws Exception {
        var user=account("original_alice");var session=login(user.getUsername());
        mvc.perform(put("/api/auth/username").header("Authorization","Bearer "+session.getAccessToken())
            .contentType("application/json").content("{\"username\":\" New_Alice \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("new_alice")).andExpect(jsonPath("$.canChange").value(false));
        assertEquals("original_alice",users.findById(user.getId()).orElseThrow().getUsername());
        var renamed=login("new_alice");assertEquals("new_alice",renamed.getDisplayUsername());assertEquals("original_alice",renamed.getUsername());
        assertEquals("original_alice",jwt.getClaimsFromToken(renamed.getAccessToken()).getSubject());
        assertThrows(RuntimeException.class,()->login("original_alice"));
        assertEquals("new_alice",login(user.getEmail()).getDisplayUsername());
        assertEquals("new_alice",auth.refreshToken(session.getRefreshToken()).getDisplayUsername());
        mvc.perform(get("/api/auth/username").header("Authorization","Bearer "+session.getAccessToken())).andExpect(status().isOk());
        mvc.perform(put("/api/auth/username").header("Authorization","Bearer "+session.getAccessToken()).contentType("application/json").content("{\"username\":\"new_alice\"}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/auth/username").header("Authorization","Bearer "+session.getAccessToken()).contentType("application/json").content("{\"username\":\"another_alice\"}"))
            .andExpect(status().isConflict());
    }
    @Test void duplicatesAndInvalidNamesDoNotConsumeAllowanceAndAnonymousRequestsFail() throws Exception {
        account("reserved_name");var user=account("other_person");var s=login(user.getUsername());
        mvc.perform(get("/api/auth/username")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/auth/username").contentType("application/json").content("{\"username\":\"anonymous\"}")).andExpect(status().isUnauthorized());
        for(String name:new String[]{"bad name","abc","abcdefghijklmnopqrstu"}){
            mvc.perform(put("/api/auth/username").header("Authorization","Bearer "+s.getAccessToken()).contentType("application/json").content("{\"username\":\""+name+"\"}"))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/auth/username").header("Authorization","Bearer "+s.getAccessToken()).contentType("application/json").content("{\"username\":\"RESERVED_NAME\"}"))
            .andExpect(status().isConflict());
        assertTrue(usernames.current(user.getUsername()).canChange());
        usernames.change(user.getUsername(),"unique_person");
        var request=new RegisterRequest();request.setUsername("UNIQUE_PERSON");request.setEmail("not-registered@example.test");request.setPassword("Password123!");
        assertThrows(RuntimeException.class,()->auth.registerUser(request));
    }
    @Test void googleAccountGetsSetupChoiceAndKeepsChosenNameOnSubsequentLogin() {
        var identity=new GoogleOAuthClient.Identity("username-google-subject","username.google@gmail.com",true,null);
        var first=auth.loginWithGoogle(identity);assertTrue(first.isUsernameSetupSuggested());
        usernames.change(first.getUsername(),"google_traveller");
        var second=auth.loginWithGoogle(identity);assertEquals(first.getUsername(),second.getUsername());
        assertEquals("google_traveller",second.getDisplayUsername());assertFalse(second.isUsernameSetupSuggested());assertFalse(second.isUsernameChangeAllowed());
    }
}
