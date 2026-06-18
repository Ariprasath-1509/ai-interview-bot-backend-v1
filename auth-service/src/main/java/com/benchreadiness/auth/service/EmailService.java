package com.benchreadiness.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Value("${app.login-url}")
    private String loginUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendCandidateWelcomeEmail(String toEmail, String candidateName, String username, String plainPassword) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }
        String name = (candidateName != null && !candidateName.isBlank()) ? candidateName : "Candidate";
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail.trim());
            helper.setSubject("Welcome to Bench Readiness — Your Login Credentials");
            helper.setText(buildCandidateWelcomeTemplate(name, username, plainPassword), true);
            mailSender.send(mimeMessage);
            log.info("Candidate welcome email sent to {}", toEmail);
        } catch (MessagingException e) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail.trim());
            message.setSubject("Welcome to Bench Readiness — Your Login Credentials");
            message.setText(buildCandidateWelcomePlainText(name, username, plainPassword));
            mailSender.send(message);
            log.info("Candidate welcome email (plain text) sent to {}", toEmail);
        } catch (Exception e) {
            log.warn("Failed to send candidate welcome email to {}: {}", toEmail, e.getMessage());
            throw e;
        }
    }

    public void sendCandidateRegistrationWelcomeEmail(String toEmail, String candidateName) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }
        String name = (candidateName != null && !candidateName.isBlank()) ? candidateName : "Candidate";
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail.trim());
            helper.setSubject("Welcome to Bench Readiness");
            helper.setText(buildCandidateRegistrationWelcomeTemplate(name), true);
            mailSender.send(mimeMessage);
            log.info("Candidate registration welcome email sent to {}", toEmail);
        } catch (MessagingException e) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail.trim());
            message.setSubject("Welcome to Bench Readiness");
            message.setText("Hi " + name + ",\n\nYour Bench Readiness account has been created successfully."
                + "\n\nLog in at: " + loginUrl
                + "\n\nUse the email and password you registered with."
                + "\n\nBench Readiness Team");
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Failed to send registration welcome email to {}: {}", toEmail, e.getMessage());
            throw e;
        }
    }

    public void sendOtpEmail(String toEmail, String otp, String userName) {
        String name = (userName != null && !userName.isBlank()) ? userName : "User";
        
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("Password Reset OTP - Bench Readiness");
            
            String htmlContent = buildOtpEmailTemplate(name, otp);
            helper.setText(htmlContent, true);
            
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            // Fallback to plain text
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("Password Reset OTP - Bench Readiness");
            message.setText(
                "Hi " + name + ",\n\n" +
                "You requested to reset your password. Use the OTP below to proceed:\n\n" +
                "OTP: " + otp + "\n\n" +
                "This OTP is valid for 10 minutes.\n\n" +
                "If you didn't request this, please ignore this email.\n\n" +
                "Bench Readiness Team"
            );
            mailSender.send(message);
        }
    }

    private String buildOtpEmailTemplate(String userName, String otp) {
        String expiryTime = LocalDateTime.now().plusMinutes(10).format(DateTimeFormatter.ofPattern("hh:mm a"));
        
        return "<!DOCTYPE html>" +
            "<html lang='en'>" +
            "<head>" +
            "<meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
            "<title>Password Reset OTP</title>" +
            "</head>" +
            "<body style='margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"Helvetica Neue\", Arial, sans-serif; background-color: #f5f5f5;'>" +
            "<table width='100%' cellpadding='0' cellspacing='0' style='background-color: #f5f5f5; padding: 40px 20px;'>" +
            "<tr><td align='center'>" +
            "<table width='600' cellpadding='0' cellspacing='0' style='background-color: #ffffff; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); overflow: hidden;'>" +
            
            "<!-- Header -->" +
            "<tr><td style='background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 40px 30px; text-align: center;'>" +
            "<div style='background-color: rgba(255,255,255,0.95); display: inline-block; padding: 15px 30px; border-radius: 50px; margin-bottom: 20px;'>" +
            "<h1 style='margin: 0; color: #667eea; font-size: 28px; font-weight: 700; letter-spacing: -0.5px;'>" +
            "<span style='color: #764ba2;'>●</span> Bench Readiness" +
            "</h1>" +
            "</div>" +
            "<h2 style='margin: 0; color: #ffffff; font-size: 24px; font-weight: 600;'>Password Reset Request</h2>" +
            "</td></tr>" +
            
            "<!-- Content -->" +
            "<tr><td style='padding: 40px 30px;'>" +
            "<p style='margin: 0 0 20px; color: #333333; font-size: 16px; line-height: 1.6;'>" +
            "Hi <strong>" + userName + "</strong>," +
            "</p>" +
            "<p style='margin: 0 0 25px; color: #555555; font-size: 15px; line-height: 1.6;'>" +
            "We received a request to reset your password. Use the One-Time Password (OTP) below to proceed with resetting your password." +
            "</p>" +
            
            "<!-- OTP Box -->" +
            "<div style='background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; margin: 30px 0; border-radius: 12px; text-align: center;'>" +
            "<p style='margin: 0 0 10px; color: rgba(255,255,255,0.9); font-size: 14px; font-weight: 600; text-transform: uppercase; letter-spacing: 1px;'>Your OTP Code</p>" +
            "<div style='background-color: rgba(255,255,255,0.95); display: inline-block; padding: 20px 40px; border-radius: 8px; margin: 10px 0;'>" +
            "<p style='margin: 0; color: #667eea; font-size: 36px; font-weight: 700; letter-spacing: 8px; font-family: \"Courier New\", monospace;'>" + otp + "</p>" +
            "</div>" +
            "<p style='margin: 15px 0 0; color: rgba(255,255,255,0.8); font-size: 13px;'>Valid until " + expiryTime + " (10 minutes)</p>" +
            "</div>" +
            
            "<!-- Security Notice -->" +
            "<div style='background-color: #fef3c7; border-left: 4px solid #f59e0b; padding: 15px 20px; margin: 25px 0; border-radius: 8px;'>" +
            "<p style='margin: 0; color: #92400e; font-size: 14px; line-height: 1.6;'>" +
            "<strong style='color: #b45309;'>⚠️ Security Notice:</strong> If you didn't request this password reset, please ignore this email and ensure your account is secure. The OTP will expire automatically." +
            "</p>" +
            "</div>" +
            
            "<p style='margin: 25px 0 0; color: #555555; font-size: 15px; line-height: 1.6;'>" +
            "Enter this OTP on the password reset page to create your new password." +
            "</p>" +
            "</td></tr>" +
            
            "<!-- Footer -->" +
            "<tr><td style='background-color: #f7fafc; padding: 30px; text-align: center; border-top: 1px solid #e2e8f0;'>" +
            "<p style='margin: 0 0 10px; color: #2d3748; font-size: 16px; font-weight: 600;'>Best regards,</p>" +
            "<p style='margin: 0 0 15px; color: #667eea; font-size: 18px; font-weight: 700;'>Bench Readiness Team</p>" +
            "<p style='margin: 0 0 10px; color: #718096; font-size: 13px;'>AI-powered technical interview platform</p>" +
            "<p style='margin: 0; color: #a0aec0; font-size: 12px;'>" +
            "Need help? Contact us at <a href='mailto:" + from + "' style='color: #667eea; text-decoration: none;'>" + from + "</a>" +
            "</p>" +
            "</td></tr>" +
            
            "</table>" +
            "</td></tr>" +
            "</table>" +
            "</body>" +
            "</html>";
    }

    private String buildCandidateWelcomePlainText(String candidateName, String username, String plainPassword) {
        return "Hi " + candidateName + ",\n\n"
            + "Welcome to Bench Readiness! Your candidate account has been created.\n\n"
            + "Login URL: " + loginUrl + "\n"
            + "Username (Email): " + username + "\n"
            + "Password: " + plainPassword + "\n\n"
            + "Please log in and change your password after your first sign-in.\n\n"
            + "Bench Readiness Team";
    }

    private String buildCandidateWelcomeTemplate(String candidateName, String username, String plainPassword) {
        return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'><title>Welcome to Bench Readiness</title></head>"
            + "<body style='margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,Arial,sans-serif;background:#f5f5f5;'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' style='background:#f5f5f5;padding:40px 20px;'><tr><td align='center'>"
            + "<table width='600' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:12px;box-shadow:0 4px 6px rgba(0,0,0,0.1);overflow:hidden;'>"
            + "<tr><td style='background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);padding:40px 30px;text-align:center;'>"
            + "<h2 style='margin:0;color:#ffffff;font-size:24px;font-weight:600;'>Welcome to Bench Readiness</h2></td></tr>"
            + "<tr><td style='padding:40px 30px;'>"
            + "<p style='margin:0 0 20px;color:#333;font-size:16px;'>Hi <strong>" + escapeHtml(candidateName) + "</strong>,</p>"
            + "<p style='margin:0 0 25px;color:#555;font-size:15px;line-height:1.6;'>"
            + "Your candidate account has been created. Use the credentials below to sign in and access interviews, assessments, and your dashboard."
            + "</p>"
            + "<div style='background:#f7fafc;border-left:4px solid #667eea;padding:20px;margin:25px 0;border-radius:8px;'>"
            + "<p style='margin:0 0 12px;color:#2d3748;font-size:14px;font-weight:600;text-transform:uppercase;'>Your Login Credentials</p>"
            + "<table width='100%' cellpadding='0' cellspacing='0'>"
            + credentialRow("Login URL", loginUrl)
            + credentialRow("Username (Email)", username)
            + credentialRow("Password", plainPassword)
            + "</table></div>"
            + "<div style='text-align:center;margin:30px 0;'>"
            + "<a href='" + loginUrl + "' style='display:inline-block;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);color:#ffffff;text-decoration:none;padding:14px 32px;border-radius:8px;font-weight:600;'>Sign In Now</a>"
            + "</div>"
            + "<p style='margin:0;color:#718096;font-size:13px;line-height:1.6;'>"
            + "For security, please change your password after your first login. If you did not expect this email, contact "
            + "<a href='mailto:" + from + "' style='color:#667eea;'>" + from + "</a>."
            + "</p></td></tr>"
            + "<tr><td style='background:#f7fafc;padding:30px;text-align:center;border-top:1px solid #e2e8f0;'>"
            + "<p style='margin:0;color:#667eea;font-size:16px;font-weight:700;'>Bench Readiness Team</p>"
            + "</td></tr></table></td></tr></table></body></html>";
    }

    private String buildCandidateRegistrationWelcomeTemplate(String candidateName) {
        return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'><title>Welcome to Bench Readiness</title></head>"
            + "<body style='margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,Arial,sans-serif;background:#f5f5f5;'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' style='background:#f5f5f5;padding:40px 20px;'><tr><td align='center'>"
            + "<table width='600' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:12px;box-shadow:0 4px 6px rgba(0,0,0,0.1);overflow:hidden;'>"
            + "<tr><td style='background:linear-gradient(135deg,#10b981 0%,#059669 100%);padding:40px 30px;text-align:center;'>"
            + "<h2 style='margin:0;color:#ffffff;font-size:24px;font-weight:600;'>Welcome to Bench Readiness</h2></td></tr>"
            + "<tr><td style='padding:40px 30px;'>"
            + "<p style='margin:0 0 20px;color:#333;font-size:16px;'>Hi <strong>" + escapeHtml(candidateName) + "</strong>,</p>"
            + "<p style='margin:0 0 25px;color:#555;font-size:15px;line-height:1.6;'>"
            + "Thank you for registering. Your account is ready — sign in with the email and password you chose during registration."
            + "</p>"
            + "<div style='text-align:center;margin:30px 0;'>"
            + "<a href='" + loginUrl + "' style='display:inline-block;background:linear-gradient(135deg,#10b981 0%,#059669 100%);color:#ffffff;text-decoration:none;padding:14px 32px;border-radius:8px;font-weight:600;'>Go to Login</a>"
            + "</div>"
            + "<p style='margin:0;color:#718096;font-size:13px;'>We're glad to have you on the platform.</p>"
            + "</td></tr></table></td></tr></table></body></html>";
    }

    private String credentialRow(String label, String value) {
        return "<tr><td style='color:#718096;font-size:14px;padding:8px 0;border-bottom:1px solid #e2e8f0;'>"
            + escapeHtml(label) + ":</td>"
            + "<td style='color:#2d3748;font-size:14px;font-weight:600;padding:8px 0;text-align:right;border-bottom:1px solid #e2e8f0;'>"
            + escapeHtml(value) + "</td></tr>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
