package com.app.trashmasters.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.mail.from-name:Trash Masters Support}")
    private String fromName;

    public void sendPasswordResetEmail(String toEmail, String employeeName, String resetToken) throws MessagingException {
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        try {
            helper.setFrom(fromEmail, fromName);
        } catch (java.io.UnsupportedEncodingException e) {
            // Fallback: set from without name if encoding fails
            helper.setFrom(fromEmail);
        }

        helper.setTo(toEmail);
        helper.setSubject("🔐 Trash Masters Password Reset Request");

        // Build HTML email using StringBuilder
        String htmlContent = buildPasswordResetEmail(employeeName, resetLink);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }

    private String buildPasswordResetEmail(String employeeName, String resetLink) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>");
        html.append("<html>");
        html.append("<head>");
        html.append("  <meta charset=\"UTF-8\">");
        html.append("  <style>");
        html.append("    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.6; color: #2d3748; background-color: #f7fafc; margin: 0; padding: 0; }");
        html.append("    .container { max-width: 600px; margin: 40px auto; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1); }");
        html.append("    .header { background: linear-gradient(135deg, #38a169 0%, #2f855a 100%); padding: 40px 30px; text-align: center; }");
        html.append("    .logo { font-size: 32px; margin-bottom: 10px; }");
        html.append("    .company-name { color: white; font-size: 24px; font-weight: 700; margin: 0; }");
        html.append("    .content { padding: 40px 30px; }");
        html.append("    .greeting { font-size: 20px; font-weight: 600; color: #1a202c; margin-bottom: 16px; }");
        html.append("    .message { font-size: 16px; color: #4a5568; margin-bottom: 24px; line-height: 1.8; }");
        html.append("    .reset-button { display: inline-block; background: #38a169; color: white; text-decoration: none; padding: 14px 40px; border-radius: 8px; font-weight: 600; font-size: 16px; margin: 20px 0; transition: all 0.3s; }");
        //                                                                                                                                                 ^^^^^^^^^^^^
        //                                                                                                                                                 This makes text WHITE
        html.append("    .reset-button:hover { background: #2f855a; }");
        html.append("    .reset-link { word-break: break-all; color: #3182ce; font-size: 14px; }");
        html.append("    .warning-box { background: #fff5f5; border-left: 4px solid #e53e3e; padding: 16px; margin: 24px 0; border-radius: 4px; }");
        html.append("    .warning-text { color: #c53030; font-size: 14px; margin: 0; }");
        html.append("    .footer { background: #f7fafc; padding: 30px; text-align: center; border-top: 1px solid #e2e8f0; }");
        html.append("    .footer-text { color: #718096; font-size: 14px; margin: 8px 0; }");
        html.append("  </style>");
        html.append("</head>");
        html.append("<body>");
        html.append("  <div class=\"container\">");
        html.append("    <div class=\"header\">");
        html.append("      <div class=\"logo\">♻️</div>");
        html.append("      <h1 class=\"company-name\">Trash Masters Co.</h1>");
        html.append("    </div>");
        html.append("    <div class=\"content\">");
        html.append("      <p class=\"greeting\">Hello ").append(employeeName).append(",</p>");
        html.append("      <p class=\"message\">");
        html.append("        We received a request to reset your password for your Trash Masters account. ");
        html.append("        Click the button below to reset your password:");
        html.append("      </p>");
        html.append("      <div style=\"text-align: center;\">");
        html.append("        <a href=\"").append(resetLink).append("\" class=\"reset-button\">Reset Password</a>");
        html.append("      </div>");
        html.append("      <p class=\"message\" style=\"text-align: center;\">");
        html.append("        Or copy and paste this link into your browser:");
        html.append("      </p>");
        html.append("      <p class=\"reset-link\" style=\"text-align: center;\">").append(resetLink).append("</p>");
        html.append("      <div class=\"warning-box\">");
        html.append("        <p class=\"warning-text\">");
        html.append("          ⚠️ <strong>Important:</strong> This link will expire in 1 hour for security reasons. ");
        html.append("          If you didn't request this password reset, please ignore this email.");
        html.append("        </p>");
        html.append("      </div>");
        html.append("    </div>");
        html.append("    <div class=\"footer\">");
        html.append("      <p class=\"footer-text\">© 2025 Trash Masters Co. All rights reserved.</p>");
        html.append("      <p class=\"footer-text\" style=\"font-size: 12px;\">This is an automated message, please do not reply.</p>");
        html.append("    </div>");
        html.append("  </div>");
        html.append("</body>");
        html.append("</html>");

        return html.toString();
    }
}