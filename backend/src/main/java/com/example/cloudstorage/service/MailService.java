package com.example.cloudstorage.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(String to, String username, String token) {
        String subject = "Verify your email – picsforyou.cloud";

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin:0;padding:0;background-color:#0a0a0a;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Oxygen,Ubuntu,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#0a0a0a;padding:40px 20px;">
                <tr>
                  <td align="center">
                    <table width="480" cellpadding="0" cellspacing="0" style="background-color:#111111;border:1px solid #2a2a2a;border-radius:12px;overflow:hidden;">
                      <!-- Header -->
                      <tr>
                        <td style="padding:32px 32px 0 32px;text-align:center;">
                          <div style="display:inline-block;padding:6px 16px;border:1px solid #FF3E00;border-radius:20px;font-size:11px;font-weight:700;letter-spacing:2px;text-transform:uppercase;color:#FF3E00;margin-bottom:16px;">
                            Email Verification
                          </div>
                          <h1 style="margin:0;font-size:28px;font-weight:900;letter-spacing:-1px;color:#eeeeee;">
                            picsforyou<span style="color:#FF3E00;">.cloud</span>
                          </h1>
                          <p style="color:#888888;font-size:13px;margin:8px 0 0 0;">Cloud Storage API</p>
                        </td>
                      </tr>
                      <!-- Divider -->
                      <tr>
                        <td style="padding:24px 32px 0 32px;">
                          <div style="height:1px;background:linear-gradient(to right,transparent,#2a2a2a,transparent);"></div>
                        </td>
                      </tr>
                      <!-- Body -->
                      <tr>
                        <td style="padding:24px 32px 0 32px;">
                          <p style="color:#cccccc;font-size:14px;line-height:1.6;margin:0 0 16px 0;">
                            Hello <strong style="color:#eeeeee;">%s</strong>,
                          </p>
                          <p style="color:#999999;font-size:13px;line-height:1.6;margin:0 0 20px 0;">
                            Thank you for registering. Use the verification code below to activate your account:
                          </p>
                          <!-- Token Box -->
                          <div style="background-color:#0a0a0a;border:1px solid #2a2a2a;border-radius:8px;padding:20px;text-align:center;margin-bottom:20px;">
                            <span style="font-family:'Courier New',monospace;font-size:16px;font-weight:700;color:#FF3E00;letter-spacing:3px;word-break:break-all;">%s</span>
                          </div>
                          <p style="color:#999999;font-size:12px;line-height:1.5;margin:0 0 24px 0;">
                            Send a POST request to <code style="background:#0a0a0a;color:#FF3E00;padding:2px 6px;border-radius:4px;font-size:11px;">/api/v1/auth/verify</code> with the token above to complete verification.
                          </p>
                        </td>
                      </tr>
                      <!-- Footer -->
                      <tr>
                        <td style="padding:24px 32px 32px 32px;">
                          <div style="height:1px;background:linear-gradient(to right,transparent,#2a2a2a,transparent);margin-bottom:20px;"></div>
                          <p style="color:#555555;font-size:11px;line-height:1.5;margin:0;text-align:center;">
                            &copy; 2026 picsforyou.cloud &mdash; Cloud Storage API Platform
                          </p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(username, token);

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            helper.setFrom("info@picsforyou.cloud");
            mailSender.send(msg);
            log.info("Verification email sent to {} — token: {}", to, token);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {} — token: {}", to, e.getMessage(), token);
        }
    }

    public void sendCustomPlanRequestEmail(String userEmail, String currentPlan, String message) {
        String subject = "Custom Plan Request from " + userEmail;

        String html = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background-color:#0a0a0a;font-family:sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 20px;">
                <tr><td align="center">
                  <table width="480" cellpadding="0" cellspacing="0" style="background:#111;border:1px solid #2a2a2a;border-radius:12px;">
                    <tr><td style="padding:32px;">
                      <div style="border:1px solid #FF3E00;border-radius:20px;font-size:11px;font-weight:700;letter-spacing:2px;text-transform:uppercase;color:#FF3E00;display:inline-block;padding:6px 16px;margin-bottom:16px;">
                        Custom Plan Request
                      </div>
                      <h1 style="color:#eee;font-size:24px;margin:0 0 16px 0;">New Custom Plan Request</h1>
                      <table style="width:100%%;font-size:13px;color:#ccc;line-height:1.6;">
                        <tr><td style="padding:8px 0;border-bottom:1px solid #2a2a2a;"><strong style="color:#eee;">User:</strong></td><td style="padding:8px 0;border-bottom:1px solid #2a2a2a;">%s</td></tr>
                        <tr><td style="padding:8px 0;border-bottom:1px solid #2a2a2a;"><strong style="color:#eee;">Current Plan:</strong></td><td style="padding:8px 0;border-bottom:1px solid #2a2a2a;">%s</td></tr>
                      </table>
                      <div style="margin-top:16px;padding:16px;background:#0a0a0a;border:1px solid #2a2a2a;border-radius:8px;">
                        <p style="color:#999;font-size:12px;margin:0 0 8px 0;text-transform:uppercase;letter-spacing:1px;">User Message:</p>
                        <p style="color:#eee;font-size:13px;margin:0;line-height:1.5;">%s</p>
                      </div>
                      <p style="color:#555;font-size:11px;margin-top:20px;text-align:center;">picsforyou.cloud - Custom Plan Request</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(userEmail, currentPlan, message);

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setTo("info@picsforyou.cloud");
            helper.setSubject(subject);
            helper.setText(html, true);
            helper.setFrom("info@picsforyou.cloud");
            helper.setReplyTo(userEmail);
            mailSender.send(msg);
            log.info("Custom plan request email sent for user {}", userEmail);
        } catch (Exception e) {
            log.error("Failed to send custom plan request email: {}", e.getMessage());
        }
    }
}
