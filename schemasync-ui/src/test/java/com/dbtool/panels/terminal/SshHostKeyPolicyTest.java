package com.dbtool.panels.terminal;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class SshHostKeyPolicyTest {

    @TempDir
    Path tempDir;

    @Test
    void testConfiguresAskCheckingKnownHostsAndFailsClosedOnMismatch() throws Exception {
        Path knownHosts = tempDir.resolve("known_hosts");
        JSch jsch = new JSch();
        Session session = jsch.getSession("ops", "127.0.0.1", 2222);

        AtomicReference<String> seenPrompt = new AtomicReference<>();
        SshHostKeyPolicy.configure(jsch, session, knownHosts, prompt -> {
            seenPrompt.set(prompt);
            return true;
        });

        assertTrue(Files.exists(knownHosts), "known_hosts file must be initialized");
        assertEquals("ask", session.getConfig("StrictHostKeyChecking"));

        UserInfo ui = session.getUserInfo();
        assertNotNull(ui);

        // First-time unknown host fingerprint delegates to TOFU prompter
        assertTrue(ui.promptYesNo("The authenticity of host '127.0.0.1' can't be established.\nSHA256 fingerprint is abc."));
        assertNotNull(seenPrompt.get());

        // Remote host key change / MITM warning must ALWAYS fail closed (return false)
        assertFalse(ui.promptYesNo("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n@    WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED!     @\n@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@"));
    }
}
