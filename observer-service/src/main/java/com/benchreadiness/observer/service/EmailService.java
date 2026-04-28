package com.benchreadiness.observer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.mail.username}")
    private String from;

    @Value("${app.interview-base-url}")
    private String interviewBaseUrl;

    @Value("${app.auth-service-url}")
    private String authServiceUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendInterviewInvite(String toEmail, String candidateName, String interviewId) {
        String name = (candidateName != null && !candidateName.isBlank()) ? candidateName : "Candidate";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Your Technical Interview Has Been Scheduled");
        message.setText(
            "Hi " + name + ",\n\n" +
            "Your technical interview has been scheduled. Please use the link below to access it:\n\n" +
            interviewBaseUrl + "/" + interviewId + "\n\n" +
            "Log in with your credentials to begin when you're ready.\n\n" +
            "Good luck!\n" +
            "Bench Readiness Team"
        );
        mailSender.send(message);
    }

    public void sendInterviewAbandoned(String createdByUserId, String interviewId, String reason) {
        // Look up manager email from auth-service
        String managerEmail = null;
        try {
            String response = restTemplate.getForObject(
                authServiceUrl + "/auth/users/" + createdByUserId, String.class);
            JsonNode node = objectMapper.readTree(response);
            managerEmail = node.path("email").asText(null);
        } catch (Exception e) {
            // Can't look up manager — skip email
            return;
        }
        if (managerEmail == null || managerEmail.isBlank()) return;

        String subject = "not_prepared".equals(reason)
            ? "Interview ended early — candidate not prepared"
            : "Interview ended — time limit reached";

        String body = "not_prepared".equals(reason)
            ? "A candidate has ended their interview early indicating they are not prepared.\n\n"
            : "A candidate's interview has ended as the 30-minute time limit was reached.\n\n";

        body += "Interview ID: " + interviewId + "\n" +
                "Review link: " + interviewBaseUrl.replace("/interview", "") + "/admin/interviews/" + interviewId + "/review\n\n" +
                "Please review and sign off when ready.\n\n" +
                "Bench Readiness Team";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(managerEmail);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
