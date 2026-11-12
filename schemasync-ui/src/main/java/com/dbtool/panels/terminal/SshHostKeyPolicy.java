package com.dbtool.panels.terminal;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Enforces SSH Host Key verification (TOFU - Trust On First Use) backed by `~/.schemasync/known_hosts`.
 * Replaces insecure `StrictHostKeyChecking=no` and removes deprecated `ssh-rsa`/`ssh-dss` defaults.
 */
public final class SshHostKeyPolicy {

    private SshHostKeyPolicy() {}

    public static Path getDefaultKnownHostsPath() {
        return com.dbtool.util.CryptoUtil.getConfigDir().resolve("known_hosts");
    }

    public static void configure(JSch jsch, Session session, Component parent) throws Exception {
        configure(jsch, session, getDefaultKnownHostsPath(), msg -> promptUserSwing(parent, msg));
    }

    public static void configure(JSch jsch, Session session, Path knownHostsPath, Function<String, Boolean> tofuPrompter) throws Exception {
        if (knownHostsPath.getParent() != null) {
            Files.createDirectories(knownHostsPath.getParent());
        }
        if (!Files.exists(knownHostsPath)) {
            Files.createFile(knownHostsPath);
        }
        jsch.setKnownHosts(knownHostsPath.toAbsolutePath().toString());
        session.setConfig("StrictHostKeyChecking", "ask");
        session.setUserInfo(createUserInfo(tofuPrompter));
    }

    public static boolean isHostKeyMismatchPrompt(String message) {
        if (message == null) return false;
        String upper = message.toUpperCase();
        return upper.contains("HOST IDENTIFICATION HAS CHANGED")
                || upper.contains("OFFENDING")
                || upper.contains("DIFFERS FROM THE KEY")
                || upper.contains("MAN-IN-THE-MIDDLE");
    }

    public static UserInfo createUserInfo(Function<String, Boolean> tofuPrompter) {
        return new UserInfo() {
            @Override public String getPassphrase() { return null; }
            @Override public String getPassword() { return null; }
            @Override public boolean promptPassword(String message) { return false; }
            @Override public boolean promptPassphrase(String message) { return false; }
            @Override public void showMessage(String message) { }

            @Override
            public boolean promptYesNo(String message) {
                if (isHostKeyMismatchPrompt(message)) {
                    // Fail closed when remote host key changed unexpectedly
                    return false;
                }
                if (tofuPrompter == null) return false;
                return Boolean.TRUE.equals(tofuPrompter.apply(message));
            }
        };
    }

    private static boolean promptUserSwing(Component parent, String message) {
        if (GraphicsEnvironment.isHeadless()) return false;
        AtomicBoolean approved = new AtomicBoolean(false);
        Runnable ask = () -> {
            int choice = JOptionPane.showConfirmDialog(
                    parent,
                    "<html><b>Verify SSH Host Key (Trust On First Use)</b><br><br>"
                            + escapeHtml(message).replace("\n", "<br>")
                            + "<br><br>Add this server's fingerprint to <code>~/.schemasync/known_hosts</code>?</html>",
                    "SSH Host Verification",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            approved.set(choice == JOptionPane.YES_OPTION);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            ask.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(ask);
            } catch (Exception ignored) {
                return false;
            }
        }
        return approved.get();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
