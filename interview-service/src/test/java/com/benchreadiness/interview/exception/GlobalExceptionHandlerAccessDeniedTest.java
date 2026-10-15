package com.benchreadiness.interview.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Report item 62: a @PreAuthorize refusal must answer 403 (not 500 from the catch-all) and must
 * not log a stack trace. Uses real method security, so the exception is the one Spring raises.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = GlobalExceptionHandlerAccessDeniedTest.TestConfig.class)
class GlobalExceptionHandlerAccessDeniedTest {

    @Configuration
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler advice() { return new GlobalExceptionHandler(); }
        @Bean SecuredController controller() { return new SecuredController(); }
    }

    @RestController
    static class SecuredController {
        @GetMapping("/secured")
        @PreAuthorize("hasRole('ADMIN')")
        public String secured() { return "ok"; }

        @GetMapping("/boom")
        public String boom() { throw new RuntimeException("something broke"); }
    }

    @Autowired WebApplicationContext context;
    private MockMvc mvc;
    private ListAppender<ILoggingEvent> appender;
    private Logger handlerLogger;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        handlerLogger.detachAppender(appender);
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            email, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void wrongRoleGets403WithErrorBodyAndNoStackTrace() throws Exception {
        loginAs("candidate@example.com", "CANDIDATE");

        mvc.perform(get("/secured"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.message").value("Access denied"))
            .andExpect(jsonPath("$.path").value("/secured"));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getFormattedMessage()).contains("/secured").doesNotContain("candidate@example.com");
    }

    @Test
    void allowedRoleStillGets200() throws Exception {
        loginAs("admin@example.com", "ADMIN");
        mvc.perform(get("/secured")).andExpect(status().isOk());
        assertThat(appender.list).isEmpty();
    }

    @Test
    void otherExceptionsStill500WithStackTraceLogged() throws Exception {
        mvc.perform(get("/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."));

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(appender.list.get(0).getThrowableProxy()).isNotNull();
    }
}
