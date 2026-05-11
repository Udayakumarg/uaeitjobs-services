package com.uaeitjobs.service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class EmailService {
    private final String sendGridKey;
    private final String fromEmail;
    private final String fromName;
    private final String frontendUrl;

    public EmailService(@Value("${sendgrid.api-key:}") String sendGridKey,
                        @Value("${sendgrid.from-email:noreply@uaeitjobs.com}") String fromEmail,
                        @Value("${sendgrid.from-name:UAEITJOBS}") String fromName,
                        @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.sendGridKey = sendGridKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.frontendUrl = frontendUrl;
    }

    public void sendVerification(String email, String token) {
        sendVerificationEmail(email, token);
    }

    public void sendVerificationEmail(String toEmail, String token) {
        String verifyLink = frontendUrl + "/verify-email?token=" + token;
        String htmlContent = """
                <h2>Verify Your Email</h2>
                <p>Click the link below to verify your UAEITJOBS account:</p>
                <p><a href="%s" style="background:#007bff;color:white;padding:10px 20px;text-decoration:none;border-radius:5px;">Verify Email</a></p>
                <p>This link expires in 24 hours.</p>
                """.formatted(escapeHtml(verifyLink));
        sendEmailOrLog(toEmail, "Verify Your UAEITJOBS Account", htmlContent);
    }

    public void sendJobApplicationConfirmation(String toEmail, String jobTitle, String companyName) {
        String htmlContent = """
                <h2>Application Submitted</h2>
                <p>Your application for <strong>%s</strong> at <strong>%s</strong> has been submitted.</p>
                <p>The hiring team will review your profile and contact you soon.</p>
                """.formatted(escapeHtml(jobTitle), escapeHtml(companyName));
        sendEmailOrLog(toEmail, "Application Confirmation", htmlContent);
    }

    public void notifyNewApplicant(String hrEmail, String jobTitle, String applicantName, String applicantEmail) {
        String dashboardLink = frontendUrl + "/hr/dashboard/applicants";
        String htmlContent = """
                <h2>New Application Received</h2>
                <p><strong>%s</strong> (%s) applied for:</p>
                <p><strong>%s</strong></p>
                <p><a href="%s" style="background:#28a745;color:white;padding:10px 20px;text-decoration:none;border-radius:5px;">View Applicant</a></p>
                """.formatted(escapeHtml(applicantName), escapeHtml(applicantEmail), escapeHtml(jobTitle), escapeHtml(dashboardLink));
        sendEmailOrLog(hrEmail, "New Job Application: " + jobTitle, htmlContent);
    }

    public void sendApplicationStatusUpdate(String toEmail, String jobTitle, String status) {
        String statusMessage = switch (status) {
            case "shortlisted" -> "Congratulations. You have been shortlisted for " + jobTitle + ".";
            case "rejected" -> "Thank you for applying. The hiring team is moving forward with other candidates for " + jobTitle + ".";
            case "hired" -> "Wonderful news. You have been hired for " + jobTitle + ".";
            default -> "Your application status for " + jobTitle + " has been updated to: " + status + ".";
        };
        String htmlContent = """
                <h2>Application Status Update</h2>
                <p>%s</p>
                <p>Log in to UAEITJOBS to view more details.</p>
                """.formatted(escapeHtml(statusMessage));
        sendEmailOrLog(toEmail, "Application Status: " + status, htmlContent);
    }

    private void sendEmailOrLog(String toEmail, String subject, String htmlContent) {
        if (sendGridKey == null || sendGridKey.isBlank()) {
            log.info("SendGrid key not configured. Email to {} with subject '{}' was not sent.", toEmail, subject);
            return;
        }
        try {
            sendEmail(toEmail, subject, htmlContent);
            log.info("Email sent to {} with subject '{}'", toEmail, subject);
        } catch (IOException ex) {
            log.error("Failed to send email to {}: {}", toEmail, ex.getMessage());
            throw new IllegalStateException("Failed to send email", ex);
        }
    }

    private void sendEmail(String toEmail, String subject, String htmlContent) throws IOException {
        Email from = new Email(fromEmail, fromName);
        Email to = new Email(toEmail);
        Content content = new Content("text/html", htmlContent);
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sendGrid = new SendGrid(sendGridKey);
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        Response response = sendGrid.api(request);
        if (response.getStatusCode() >= 400) {
            throw new IOException("SendGrid API error: " + response.getStatusCode() + " " + response.getBody());
        }
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
