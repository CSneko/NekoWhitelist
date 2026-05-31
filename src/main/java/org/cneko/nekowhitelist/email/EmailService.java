package org.cneko.nekowhitelist.email;

import org.cneko.nekowhitelist.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class EmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger("NekoWhitelist:Email");
    private static final int TIMEOUT_MS = 30000;
    private static EmailService instance;

    public static EmailService getInstance() {
        if (instance == null) {
            instance = new EmailService();
        }
        return instance;
    }

    // ========================================================================
    // 公开 API
    // ========================================================================

    public CompletableFuture<Boolean> sendVerificationCode(String toEmail, String code) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                sendEmail(toEmail, "NekoWhitelist 邮箱验证码喵~", buildVerificationBody(code), null);
                return true;
            } catch (Exception e) {
                LOGGER.error("发送邮件失败喵!", e);
                return false;
            }
        });
    }

    public CompletableFuture<String> sendDebugEmail(String toEmail) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> log = new ArrayList<>();
            long startTime = System.currentTimeMillis();
            ModConfig.EmailConfig cfg = ModConfig.getInstance().email;

            log.add("══════════════════════════════════════");
            log.add("  NekoWhitelist SMTP 调试邮件喵~");
            log.add("══════════════════════════════════════");
            log.add("  目标地址: " + toEmail);
            log.add("  SMTP 服务器: " + cfg.smtpHost + ":" + cfg.smtpPort);
            log.add("  发件人: " + cfg.senderEmail);
            log.add("  用户名: " + cfg.username);
            log.add("  TLS: " + (cfg.useTls ? "STARTTLS" : "无"));
            log.add("══════════════════════════════════════");
            log.add("");

            try {
                sendEmail(toEmail, "NekoWhitelist 调试测试邮件喵~", buildDebugBody(), log);
                long elapsed = System.currentTimeMillis() - startTime;
                log.add("");
                log.add("══════════════════════════════════════");
                log.add("  ✅ 邮件发送成功喵！");
                log.add("  总耗时: " + elapsed + "ms");
                log.add("══════════════════════════════════════");
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - startTime;
                log.add("");
                log.add("══════════════════════════════════════");
                log.add("  ❌ 发送失败喵！");
                log.add("  总耗时: " + elapsed + "ms");
                log.add("  异常类型: " + e.getClass().getSimpleName());
                log.add("  异常信息: " + e.getMessage());
                // 打印堆栈到 log
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                log.add("  堆栈跟踪:");
                for (String line : sw.toString().split("\n")) {
                    log.add("    " + line.trim());
                }
                log.add("══════════════════════════════════════");
                LOGGER.error("发送调试邮件失败喵!", e);
            }
            return String.join("\n", log);
        });
    }

    // ========================================================================
    // 核心发送逻辑（log 非 null 时输出超详细日志）
    // ========================================================================

    private void sendEmail(String to, String subject, String body, List<String> log) throws IOException {
        ModConfig.EmailConfig cfg = ModConfig.getInstance().email;
        String host = cfg.smtpHost;
        int port = cfg.smtpPort;

        LOGGER.info("正在连接 SMTP 服务器: {}:{}", host, port);
        if (log != null) log.add("Step ➀ 解析服务器地址... " + host + ":" + port);

        if (port == 465) {
            if (log != null) log.add("  使用 SMTPS (隐式 SSL, 端口 465)");
            sendSmtps(host, port, cfg, to, subject, body, log);
        } else {
            if (log != null) log.add("  使用 STARTTLS (端口 " + port + ")");
            sendStartTls(host, port, cfg, to, subject, body, log);
        }
    }

    private void sendSmtps(String host, int port, ModConfig.EmailConfig cfg,
                           String to, String subject, String body, List<String> log) throws IOException {
        long t0 = System.currentTimeMillis();
        if (log != null) log.add("Step ➀ 创建 SSL 连接...");
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket) factory.createSocket();
        socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
        socket.setSoTimeout(TIMEOUT_MS);
        if (log != null) log.add("  TCP 已连接 (" + (System.currentTimeMillis() - t0) + "ms)");

        if (log != null) log.add("Step ➁ TLS 握手...");
        long t1 = System.currentTimeMillis();
        socket.startHandshake();
        if (log != null) log.add("  TLS 握手完成 (" + (System.currentTimeMillis() - t1) + "ms)");

        try {
            smtpConversation(socket, cfg, to, subject, body, log, false);  // SMTPS: 需要读 greeting
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void sendStartTls(String host, int port, ModConfig.EmailConfig cfg,
                              String to, String subject, String body, List<String> log) throws IOException {
        long stepStart;
        long totalStart = System.currentTimeMillis();

        // Step ➀: TCP 连接 + DNS 解析
        stepStart = System.currentTimeMillis();
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
        socket.setSoTimeout(TIMEOUT_MS);
        if (log != null) log.add("  [计时] TCP 连接: " + (System.currentTimeMillis() - stepStart) + "ms");

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // Step ➁: 读取服务器 greeting
            stepStart = System.currentTimeMillis();
            if (log != null) log.add("Step ➁ 读取服务器 Greeting...");
            readResponse(in, "greeting", log);
            if (log != null) log.add("  [计时] Greeting: " + (System.currentTimeMillis() - stepStart) + "ms");

            // Step ➂: EHLO
            stepStart = System.currentTimeMillis();
            if (log != null) log.add("Step ➂ 发送 EHLO...");
            sendCommand(out, in, "EHLO NekoWhitelist", "EHLO", log);
            if (log != null) log.add("  [计时] EHLO: " + (System.currentTimeMillis() - stepStart) + "ms");

            // Step ➃: STARTTLS
            stepStart = System.currentTimeMillis();
            if (log != null) log.add("Step ➃ 发送 STARTTLS...");
            sendCommand(out, in, "STARTTLS", "STARTTLS", log);
            if (log != null) log.add("  [计时] STARTTLS: " + (System.currentTimeMillis() - stepStart) + "ms");

            // Step ➄: TLS 升级
            stepStart = System.currentTimeMillis();
            if (log != null) log.add("Step ➄ TLS 升级...");
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            long t0 = System.currentTimeMillis();
            SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket, host, port, true);
            if (log != null) log.add("  [计时] createSocket: " + (System.currentTimeMillis() - t0) + "ms");
            sslSocket.setSoTimeout(TIMEOUT_MS);
            t0 = System.currentTimeMillis();
            sslSocket.startHandshake();
            if (log != null) log.add("  [计时] startHandshake: " + (System.currentTimeMillis() - t0) + "ms");
            if (log != null) log.add("  [计时] TLS 升级总计: " + (System.currentTimeMillis() - stepStart) + "ms");

            // Step ➅: Post-TLS
            if (log != null) {
                log.add("Step ➅ 进入加密会话...");
                log.add("  [计时] 从连接到进入加密会话: " + (System.currentTimeMillis() - totalStart) + "ms");
            }

            try {
                smtpConversation(sslSocket, cfg, to, subject, body, log, true);  // STARTTLS: 跳过 greeting，直接发 EHLO
            } finally {
                try { sslSocket.close(); } catch (IOException ignored) {}
            }
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void smtpConversation(Socket socket, ModConfig.EmailConfig cfg,
                                  String to, String subject, String body, List<String> log,
                                  boolean skipGreeting) throws IOException {
        long stepStart;
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        // Post-TLS / Post-connect greeting（STARTTLS 后服务器不会发 greeting，需跳过）
        if (!skipGreeting) {
            stepStart = System.currentTimeMillis();
            if (log != null) log.add("  [子步骤] 读取 Post-TLS Greeting...");
            readResponse(in, "post-TLS greeting", log);
            if (log != null) log.add("  [计时] Post-TLS Greeting: " + (System.currentTimeMillis() - stepStart) + "ms");
        }

        // EHLO again after TLS
        stepStart = System.currentTimeMillis();
        if (log != null) log.add("  [子步骤] 发送 EHLO...");
        sendCommand(out, in, "EHLO NekoWhitelist", "EHLO2", log);
        if (log != null) log.add("  [计时] EHLO2: " + (System.currentTimeMillis() - stepStart) + "ms");

        // AUTH LOGIN
        stepStart = System.currentTimeMillis();
        if (log != null) log.add("  [子步骤] 登录认证 (AUTH LOGIN)...");
        sendCommand(out, in, "AUTH LOGIN", "AUTH LOGIN", log);
        sendCommand(out, in, Base64.getEncoder().encodeToString(cfg.username.getBytes(StandardCharsets.UTF_8)), "AUTH USER", log);
        sendCommand(out, in, Base64.getEncoder().encodeToString(cfg.password.getBytes(StandardCharsets.UTF_8)), "AUTH PASS", log);
        if (log != null) log.add("  [计时] 登录: " + (System.currentTimeMillis() - stepStart) + "ms");

        // MAIL FROM
        stepStart = System.currentTimeMillis();
        if (log != null) log.add("  [子步骤] 设置发件人 (MAIL FROM)...");
        sendCommand(out, in, "MAIL FROM:<" + cfg.senderEmail + ">", "MAIL FROM", log);
        if (log != null) log.add("  [计时] MAIL FROM: " + (System.currentTimeMillis() - stepStart) + "ms");

        // RCPT TO
        stepStart = System.currentTimeMillis();
        if (log != null) log.add("  [子步骤] 设置收件人 (RCPT TO)...");
        sendCommand(out, in, "RCPT TO:<" + to + ">", "RCPT TO", log);
        if (log != null) log.add("  [计时] RCPT TO: " + (System.currentTimeMillis() - stepStart) + "ms");

        // DATA
        stepStart = System.currentTimeMillis();
        if (log != null) log.add("  [子步骤] 发送邮件内容 (DATA)...");
        sendCommand(out, in, "DATA", "DATA", log);
        if (log != null) log.add("  [计时] DATA 命令: " + (System.currentTimeMillis() - stepStart) + "ms");

        // 邮件头和正文
        String encodedSubject = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=";
        if (log != null) {
            log.add("  [子步骤] 发送邮件头和正文...");
            log.add("    From: NekoWhitelist <" + cfg.senderEmail + ">");
            log.add("    To: <" + to + ">");
            log.add("    Subject: " + subject);
            log.add("    Content-Type: text/html; charset=UTF-8");
            log.add("    正文长度: " + body.length() + " 字符");
        }
        stepStart = System.currentTimeMillis();
        out.print("From: NekoWhitelist <" + cfg.senderEmail + ">\r\n");
        out.print("To: <" + to + ">\r\n");
        out.print("Subject: " + encodedSubject + "\r\n");
        out.print("Content-Type: text/html; charset=UTF-8\r\n");
        out.print("\r\n");
        out.print(body + "\r\n");
        out.print(".\r\n");
        out.flush();
        if (log != null) log.add("  [计时] 发送邮件正文: " + (System.currentTimeMillis() - stepStart) + "ms");

        stepStart = System.currentTimeMillis();
        readResponse(in, "DATA end", log);
        if (log != null) log.add("  [计时] DATA 响应: " + (System.currentTimeMillis() - stepStart) + "ms");

        sendCommand(out, in, "QUIT", "QUIT", log);
        LOGGER.info("邮件发送成功喵~ {} -> {}", cfg.senderEmail, to);
    }

    // ========================================================================
    // SMTP 协议底层方法
    // ========================================================================

    private void sendCommand(PrintWriter out, BufferedReader in, String command, String label, List<String> log) throws IOException {
        boolean isAuth = command.startsWith("AUTH") || label.contains("AUTH");
        LOGGER.debug("SMTP >> {}", isAuth ? label : command);
        if (log != null) {
            if (isAuth) {
                log.add("  >>> " + label + " (已隐藏认证信息)");
            } else {
                log.add("  >>> " + command);
            }
        }
        out.print(command + "\r\n");
        out.flush();
        readResponse(in, label, log);
    }

    private String readResponse(BufferedReader in, String label, List<String> log) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        long tFirstLine = System.currentTimeMillis();
        boolean firstLine = true;
        while ((line = in.readLine()) != null) {
            if (firstLine) {
                if (log != null) log.add("  [计时] 等待首行响应: " + (System.currentTimeMillis() - tFirstLine) + "ms");
                firstLine = false;
            }
            if (line.isEmpty()) continue;
            sb.append(line).append("\n");
            if (log != null) log.add("  <<< " + line);
            // SMTP 多行响应: 最后一行格式是 "NNN text"，续行是 "NNN-text"
            if (line.length() >= 4 && (line.charAt(3) == ' ' || line.charAt(3) == '-')) {
                if (line.charAt(3) == ' ') break;
            } else {
                // 不标准的响应格式，可能是最后一行
                break;
            }
        }
        String response = sb.toString().trim();
        LOGGER.debug("SMTP << [{}] {}", label, response.replace("\n", " / "));

        // 检查是否是错误响应 (4xx 临时错误, 5xx 永久错误)
        if (!response.isEmpty()) {
            char firstChar = response.charAt(0);
            if ((firstChar == '4' || firstChar == '5') &&
                !response.startsWith("220") && !response.startsWith("250") &&
                !response.startsWith("334") && !response.startsWith("235") &&
                !response.startsWith("354") && !response.startsWith("221")) {
                if (log != null) log.add("  ⚠ 服务器返回异常: " + response);
                LOGGER.warn("SMTP 返回异常: {}", response);
                throw new IOException("SMTP server returned error: " + response);
            }
        }
        return response;
    }

    // ========================================================================
    // 邮件正文模板
    // ========================================================================

    private String buildVerificationBody(String code) {
        Map<String, String> vars = new HashMap<>();
        vars.put("code", code);
        vars.put("expiry_minutes", "5");
        return TemplateManager.getInstance().render("verification.html", vars);
    }

    public CompletableFuture<Boolean> sendPasswordResetCode(String toEmail, String code) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                sendEmail(toEmail, "NekoWhitelist 密码重置喵~", buildPasswordResetBody(code), null);
                return true;
            } catch (Exception e) {
                LOGGER.error("发送密码重置邮件失败喵!", e);
                return false;
            }
        });
    }

    private String buildPasswordResetBody(String code) {
        Map<String, String> vars = new HashMap<>();
        vars.put("code", code);
        vars.put("expiry_minutes", "5");
        return TemplateManager.getInstance().render("password_reset.html", vars);
    }

    private String buildDebugBody() {
        ModConfig.EmailConfig cfg = ModConfig.getInstance().email;
        Map<String, String> vars = new HashMap<>();
        vars.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        vars.put("smtp_host", cfg.smtpHost + ":" + cfg.smtpPort);
        return TemplateManager.getInstance().render("debug.html", vars);
    }
}
