package com.uaeitjobs.service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.ClickTrackingSetting;
import com.sendgrid.helpers.mail.objects.TrackingSettings;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.util.Arrays;

@Slf4j
@Service
public class EmailService {
    private final String sendGridKey;
    private final String fromEmail;
    private final String fromName;
    private final String frontendUrl;
    private final String adminEmail;
    private final Environment environment;

    public EmailService(@Value("${sendgrid.api-key:}") String sendGridKey,
                        @Value("${sendgrid.from-email:noreply@uaeitjobs.com}") String fromEmail,
                        @Value("${sendgrid.from-name:UAEITJOBS}") String fromName,
                        @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl,
                        @Value("${sendgrid.admin-email:hello@uaeitjobs.com}") String adminEmail,
                        Environment environment) {
        this.sendGridKey = sendGridKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.frontendUrl = frontendUrl;
        this.adminEmail = adminEmail;
        this.environment = environment;
    }

    public void sendVerification(String email, String token) {
        sendVerificationEmail(email, token);
    }

    @PostConstruct
    void validateProductionConfiguration() {
        if (isProduction() && (sendGridKey == null || sendGridKey.isBlank())) {
            throw new IllegalStateException("SENDGRID_API_KEY not configured in production");
        }
    }

    // ── Email templates ───────────────────────────────────────────────────────

    public void sendVerificationEmail(String toEmail, String token) {
        String verifyLink = frontendUrl + "/verify-email?token=" + token;
        String html = layout("Verify your email address",
            block("Verify your email address") +
            text("You're almost there. Click the button below to verify your email and activate your UAEITJOBS account.") +
            cta(verifyLink, "Verify Email Address") +
            muted("This link expires in 24 hours. If you didn't create an account, you can safely ignore this email."));
        sendEmailOrLog(toEmail, "Verify your UAEITJOBS account", html);
    }

    public void sendJobApplicationConfirmation(String toEmail, String jobTitle, String companyName) {
        String html = layout("Application submitted",
            block("Application submitted") +
            text("Your application for <strong>" + escapeHtml(jobTitle) + "</strong> at <strong>"
                + escapeHtml(companyName) + "</strong> has been received.") +
            text("The hiring team will review your profile and reach out if there's a match. Keep an eye on your inbox.") +
            cta(frontendUrl + "/dashboard/applications", "View My Applications") +
            muted("You're receiving this because you applied via UAEITJOBS."));
        sendEmailOrLog(toEmail, "Application received: " + jobTitle, html);
    }

    public void notifyNewApplicant(String hrEmail, String jobTitle, String applicantName, String applicantEmail) {
        String html = layout("New application received",
            block("New application received") +
            text("<strong>" + escapeHtml(applicantName) + "</strong> (" + escapeHtml(applicantEmail)
                + ") has applied for the role:") +
            highlight(escapeHtml(jobTitle)) +
            cta(frontendUrl + "/hr/dashboard/applicants", "Review Applicant") +
            muted("Manage all your applications from your UAEITJOBS HR dashboard."));
        sendEmailOrLog(hrEmail, "New applicant: " + jobTitle, html);
    }

    public void sendContactFormEmail(String senderName, String senderEmail, String subject, String message) {
        String html = layout("New contact form message",
            block("New message from " + escapeHtml(senderName)) +
            text("From: <strong>" + escapeHtml(senderName) + "</strong> &lt;" + escapeHtml(senderEmail) + "&gt;") +
            text("Subject: <strong>" + escapeHtml(subject) + "</strong>") +
            highlight(escapeHtml(message)) +
            muted("Reply directly to this email to reach " + escapeHtml(senderName) + "."));
        sendEmailOrLog(adminEmail, "[Contact] " + subject, html);
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;
        String html = layout("Reset your password",
            block("Reset your password") +
            text("We received a request to reset the password for your UAEITJOBS account. Click the button below to choose a new one.") +
            cta(resetLink, "Reset Password") +
            muted("This link expires in 1 hour. If you didn't request a password reset, you can safely ignore this email — your account has not been changed."));
        sendEmailOrLog(toEmail, "Reset your UAEITJOBS password", html);
    }

    /**
     * Proactive welcome email triggered by an admin from the friction signals table.
     * HR users get onboarding guidance; job seekers get profile-completion tips.
     */
    public void sendWelcomeEmail(String toEmail, com.uaeitjobs.entity.UserType userType, String displayName) {
        String name = (displayName != null && !displayName.isBlank()) ? displayName : toEmail;
        String html;
        String subject;
        if (userType == com.uaeitjobs.entity.UserType.hr) {
            subject = "Get started on UAEITJOBS — post your first job in minutes";
            html = layout(subject,
                block("Welcome to UAEITJOBS, " + escapeHtml(name) + "!") +
                text("Your employer account is set up and ready. Here's how to attract top UAE IT talent in three steps:") +
                highlight("1. Post a job &nbsp;→ 2. Review applicants &nbsp;→ 3. Shortlist or hire") +
                cta(frontendUrl + "/hr/jobs/new", "Post Your First Job") +
                muted("Questions? Reply to this email or visit uaeitjobs.com"));
        } else {
            subject = "Your UAEITJOBS profile is ready — complete it to get noticed";
            html = layout(subject,
                block("Welcome to UAEITJOBS, " + escapeHtml(name) + "!") +
                text("Your account is active. Complete your profile to stand out to UAE tech employers:") +
                highlight("Add your headline · Upload your CV · List your skills") +
                cta(frontendUrl + "/seeker/profile", "Complete Your Profile") +
                muted("Questions? Reply to this email or visit uaeitjobs.com"));
        }
        sendEmailOrLog(toEmail, subject, html);
    }

    public void sendApplicationStatusUpdate(String toEmail, String jobTitle, String status) {
        String heading = switch (status) {
            case "shortlisted" -> "You've been shortlisted";
            case "rejected"    -> "Application update";
            case "hired"       -> "Congratulations — you're hired!";
            default            -> "Application status update";
        };
        String body = switch (status) {
            case "shortlisted" -> "Great news — you have been shortlisted for <strong>" + escapeHtml(jobTitle) + "</strong>. The hiring team will be in touch shortly.";
            case "rejected"    -> "Thank you for your interest in <strong>" + escapeHtml(jobTitle) + "</strong>. The team has decided to move forward with other candidates at this time.";
            case "hired"       -> "We're thrilled to let you know you have been selected for <strong>" + escapeHtml(jobTitle) + "</strong>. Congratulations!";
            default            -> "Your application for <strong>" + escapeHtml(jobTitle) + "</strong> has been updated to <strong>" + escapeHtml(status) + "</strong>.";
        };
        String html = layout(heading,
            block(heading) +
            text(body) +
            cta(frontendUrl + "/dashboard/applications", "View Application") +
            muted("You're receiving this because you applied via UAEITJOBS."));
        sendEmailOrLog(toEmail, heading + ": " + jobTitle, html);
    }

    // ── HTML template helpers ─────────────────────────────────────────────────

    private String layout(String title, String body) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>%s</title>
            </head>
            <body style="margin:0;padding:0;background:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:40px 16px;">
                <tr><td align="center">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;">

                    <!-- Header -->
                    <tr><td style="padding-bottom:24px;text-align:center;">
                      <span style="font-size:18px;font-weight:700;letter-spacing:-0.3px;color:#0f172a;">UAE<span style="color:#be185d;">IT</span>JOBS</span>
                    </td></tr>

                    <!-- Card -->
                    <tr><td style="background:#ffffff;border-radius:12px;border:1px solid #e4e4e7;padding:40px 40px 32px;">
                      %s
                    </td></tr>

                    <!-- Footer -->
                    <tr><td style="padding-top:24px;text-align:center;font-size:12px;color:#a1a1aa;line-height:1.6;">
                      UAE IT Jobs &nbsp;·&nbsp; Dubai, United Arab Emirates<br/>
                      <a href="%s" style="color:#a1a1aa;">uaeitjobs.com</a>
                    </td></tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(escapeHtml(title), body, frontendUrl);
    }

    private String block(String heading) {
        return "<h1 style=\"margin:0 0 16px;font-size:22px;font-weight:700;color:#0f172a;letter-spacing:-0.3px;\">"
            + escapeHtml(heading) + "</h1>";
    }

    private String text(String html) {
        return "<p style=\"margin:0 0 16px;font-size:15px;color:#3f3f46;line-height:1.65;\">" + html + "</p>";
    }

    private String highlight(String text) {
        return "<div style=\"margin:16px 0;padding:14px 18px;background:#fdf2f8;border-left:3px solid #be185d;"
            + "border-radius:6px;font-size:15px;font-weight:600;color:#be185d;\">" + text + "</div>";
    }

    private String cta(String url, String label) {
        return "<div style=\"margin:28px 0 24px;\">"
            + "<a href=\"" + url + "\" style=\"display:inline-block;background:#be185d;color:#ffffff;"
            + "text-decoration:none;font-size:14px;font-weight:600;padding:13px 28px;"
            + "border-radius:8px;letter-spacing:0.1px;\">" + escapeHtml(label) + "</a>"
            + "</div>";
    }

    private String muted(String html) {
        return "<p style=\"margin:0;font-size:12px;color:#a1a1aa;line-height:1.6;border-top:1px solid #f4f4f5;"
            + "padding-top:16px;\">" + html + "</p>";
    }

    private void sendEmailOrLog(String toEmail, String subject, String htmlContent) {
        if (sendGridKey == null || sendGridKey.isBlank()) {
            validateProductionConfiguration();
            log.warn("SendGrid key not configured. Email to {} with subject '{}' was not sent.", toEmail, subject);
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

        // Disable click tracking — prevents SendGrid wrapping links through
        // url*.uaeitjobs.com which lacks an SSL cert, breaking verify buttons.
        ClickTrackingSetting clickTracking = new ClickTrackingSetting();
        clickTracking.setEnable(false);
        clickTracking.setEnableText(false);
        TrackingSettings tracking = new TrackingSettings();
        tracking.setClickTrackingSetting(clickTracking);
        mail.setTrackingSettings(tracking);

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
        return HtmlUtils.htmlEscape(value);
    }

    private boolean isProduction() {
        if (environment == null) {
            return hasProdProfile(System.getProperty("spring.profiles.active"))
                    || hasProdProfile(System.getenv("SPRING_PROFILES_ACTIVE"));
        }
        return Arrays.asList(environment.getActiveProfiles()).contains("prod")
                || hasProdProfile(environment.getProperty("spring.profiles.active"));
    }

    private boolean hasProdProfile(String profiles) {
        if (profiles == null || profiles.isBlank()) {
            return false;
        }
        return Arrays.stream(profiles.split(","))
                .map(String::trim)
                .anyMatch("prod"::equalsIgnoreCase);
    }
}
