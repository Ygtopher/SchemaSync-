package com.dbtool.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Enterprise In-App Auto-Updater & Instant Delta Update Studio.
 * Supports:
 * - ⚡ Lightweight Delta Patch Updates (SchemaSync-patch.jar ~1.2 MB vs 57 MB full package)
 * - 🔕 Silent Background Pre-Download & Staging with "✨ Update Ready — Restart" toolbar pill
 * - 🛠️ Direct In-Place Binary Swap for both SchemaSync.jar and Launch4j SchemaSync.exe (no setup wizard)
 * - Manual browser download of Windows/Linux installers & GitHub release browsing
 *
 * Created & Developed by: NSENGIYUMVA Christophe
 * Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.
 */
public class UpdateChecker {

    public static final String CURRENT_VERSION = com.dbtool.Main.APP_VERSION;
    public static final String GITHUB_REPO = "Ygtopher/SchemaSync-";
    public static final String API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

    private static volatile JButton toolbarUpdateButton = null;
    private static volatile String stagedVersionTag = null;
    private static volatile Map<File, File> preStagedReplacements = null;

    /**
     * Registers the main window's toolbar Update button so background update staging
     * can turn it into an interactive "✨ Update Ready (vX.Y.Z) — Restart" pill.
     */
    public static void registerToolbarButton(JButton button) {
        toolbarUpdateButton = button;
    }

    public static void checkForUpdates(Component parent, boolean silentIfUpToDate) {
        new Thread(() -> {
            try {
                URL url = new URI(API_URL).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "SchemaSync/" + CURRENT_VERSION);
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 404) {
                    if (!silentIfUpToDate) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(parent,
                                "✅ You are running the latest version of SchemaSync (v" + CURRENT_VERSION + ").\n" +
                                "No newer releases found on GitHub.",
                                "SchemaSync Update Check", JOptionPane.INFORMATION_MESSAGE);
                        });
                    }
                    return;
                }

                if (responseCode != 200) {
                    if (!silentIfUpToDate) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(parent,
                                "Unable to check for updates (HTTP " + responseCode + ").\nPlease check your internet connection or try again later.",
                                "Update Check", JOptionPane.WARNING_MESSAGE);
                        });
                    }
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                }

                ObjectMapper mapper = new ObjectMapper();
                JsonNode release = mapper.readTree(sb.toString());

                String tagName = release.path("tag_name").asText("").trim();
                String releaseName = repairMojibake(release.path("name").asText(tagName));
                String body = formatReleaseNotesForDisplay(release.path("body").asText("No release notes provided."));
                String htmlUrl = release.path("html_url").asText("https://github.com/" + GITHUB_REPO + "/releases");

                // Discover assets across platforms (Delta patch .jar, Windows .exe, Ubuntu .deb, Fedora .rpm, Universal .jar)
                String patchUrl = null;
                String patchName = null;
                long patchSize = 0;
                String setupUrl = null;
                long setupSize = 0;
                String exeUrl = null;
                long exeSize = 0;
                String debUrl = null;
                String debName = null;
                long debSize = 0;
                String rpmUrl = null;
                String rpmName = null;
                long rpmSize = 0;
                String jarUrl = null;
                String jarName = null;
                long jarSize = 0;

                JsonNode assets = release.path("assets");
                if (assets.isArray()) {
                    for (JsonNode asset : assets) {
                        String aName = asset.path("name").asText("");
                        String aUrl = asset.path("browser_download_url").asText(null);
                        long aSize = asset.path("size").asLong(0);
                        String lower = aName.toLowerCase();
                        if (lower.contains("patch") && lower.endsWith(".jar")) {
                            patchUrl = aUrl;
                            patchName = aName;
                            patchSize = aSize;
                        } else if (aName.equalsIgnoreCase("SchemaSync-Setup.exe")) {
                            setupUrl = aUrl;
                            setupSize = aSize;
                        } else if (aName.equalsIgnoreCase("SchemaSync.exe")) {
                            exeUrl = aUrl;
                            exeSize = aSize;
                            if (setupSize == 0) setupSize = aSize;
                        } else if (lower.endsWith(".deb")) {
                            debUrl = aUrl;
                            debName = aName;
                            debSize = aSize;
                        } else if (lower.endsWith(".rpm")) {
                            rpmUrl = aUrl;
                            rpmName = aName;
                            rpmSize = aSize;
                        } else if (lower.endsWith(".jar")) {
                            jarUrl = aUrl;
                            jarName = aName;
                            jarSize = aSize;
                        }
                    }
                }

                boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
                boolean hasDpkg = !isWindows && new File("/usr/bin/dpkg").exists();
                boolean hasRpm = !isWindows && !hasDpkg && new File("/usr/bin/rpm").exists();

                // 1. Manual browser download package ("📥 Download Full Installer")
                String manualDownloadUrl;
                String manualDownloadFileName;
                long manualDownloadSize;

                if (isWindows) {
                    if (setupUrl != null) {
                        manualDownloadUrl = setupUrl;
                        manualDownloadFileName = "SchemaSync-Setup-" + tagName + ".exe";
                        manualDownloadSize = setupSize;
                    } else if (exeUrl != null) {
                        manualDownloadUrl = exeUrl;
                        manualDownloadFileName = "SchemaSync-" + tagName + ".exe";
                        manualDownloadSize = exeSize;
                    } else if (jarUrl != null) {
                        manualDownloadUrl = jarUrl;
                        manualDownloadFileName = jarName != null ? jarName : "SchemaSync.jar";
                        manualDownloadSize = jarSize;
                    } else {
                        manualDownloadUrl = htmlUrl;
                        manualDownloadFileName = "SchemaSync-Setup-" + tagName + ".exe";
                        manualDownloadSize = 0;
                    }
                } else if (hasDpkg && debUrl != null) {
                    manualDownloadUrl = debUrl;
                    manualDownloadFileName = debName != null ? debName : ("schemasync_" + tagName.replaceFirst("^[vV]", "") + "_all.deb");
                    manualDownloadSize = debSize;
                } else if (hasRpm && rpmUrl != null) {
                    manualDownloadUrl = rpmUrl;
                    manualDownloadFileName = rpmName != null ? rpmName : ("schemasync-" + tagName.replaceFirst("^[vV]", "") + ".noarch.rpm");
                    manualDownloadSize = rpmSize;
                } else if (jarUrl != null) {
                    manualDownloadUrl = jarUrl;
                    manualDownloadFileName = jarName != null ? jarName : "SchemaSync.jar";
                    manualDownloadSize = jarSize;
                } else {
                    manualDownloadUrl = setupUrl != null ? setupUrl : (exeUrl != null ? exeUrl : htmlUrl);
                    manualDownloadFileName = "SchemaSync-Update";
                    manualDownloadSize = setupSize;
                }

                // 2. Automated Instant In-Place Update package:
                // Prefer lightweight SchemaSync-patch.jar (~1.2 MB) whenever local patchable binaries exist!
                List<File> localBinaries = resolveLocalPatchableBinaries();
                boolean canUseDeltaPatch = patchUrl != null && !localBinaries.isEmpty();

                String autoUpdateUrl;
                String autoUpdateFileName;
                long autoUpdateSize;
                String fallbackFullUrl = jarUrl != null ? jarUrl : (isWindows && exeUrl != null ? exeUrl : manualDownloadUrl);
                String fallbackFullFileName = jarUrl != null ? "SchemaSync.jar" : (isWindows && exeUrl != null ? "SchemaSync.exe" : manualDownloadFileName);

                if (canUseDeltaPatch) {
                    autoUpdateUrl = patchUrl;
                    autoUpdateFileName = patchName != null ? patchName : "SchemaSync-patch.jar";
                    autoUpdateSize = patchSize;
                } else if (isWindows) {
                    // Direct in-place swap without running Setup wizard if local JAR or EXE exists
                    File runningJar = getRunningJarFile();
                    File runningExe = getRunningExeFile();
                    if (runningJar != null && jarUrl != null) {
                        autoUpdateUrl = jarUrl;
                        autoUpdateFileName = "SchemaSync.jar";
                        autoUpdateSize = jarSize;
                    } else if (runningExe != null && exeUrl != null) {
                        autoUpdateUrl = exeUrl;
                        autoUpdateFileName = "SchemaSync.exe";
                        autoUpdateSize = exeSize;
                    } else if (jarUrl != null && !localBinaries.isEmpty()) {
                        autoUpdateUrl = jarUrl;
                        autoUpdateFileName = "SchemaSync.jar";
                        autoUpdateSize = jarSize;
                    } else if (setupUrl != null) {
                        autoUpdateUrl = setupUrl;
                        autoUpdateFileName = "SchemaSync-Setup-" + tagName + ".exe";
                        autoUpdateSize = setupSize;
                    } else {
                        autoUpdateUrl = manualDownloadUrl;
                        autoUpdateFileName = manualDownloadFileName;
                        autoUpdateSize = manualDownloadSize;
                    }
                } else {
                    if (jarUrl != null) {
                        autoUpdateUrl = jarUrl;
                        autoUpdateFileName = "SchemaSync.jar";
                        autoUpdateSize = jarSize;
                    } else if (hasDpkg && debUrl != null) {
                        autoUpdateUrl = debUrl;
                        autoUpdateFileName = debName != null ? debName : "schemasync_update.deb";
                        autoUpdateSize = debSize;
                    } else if (hasRpm && rpmUrl != null) {
                        autoUpdateUrl = rpmUrl;
                        autoUpdateFileName = rpmName != null ? rpmName : "schemasync_update.rpm";
                        autoUpdateSize = rpmSize;
                    } else {
                        autoUpdateUrl = manualDownloadUrl;
                        autoUpdateFileName = manualDownloadFileName;
                        autoUpdateSize = manualDownloadSize;
                    }
                }

                final String finalManualUrl = manualDownloadUrl;
                final String finalManualName = manualDownloadFileName;
                final long finalManualSize = manualDownloadSize;
                final String finalAutoUrl = autoUpdateUrl;
                final String finalAutoName = autoUpdateFileName;
                final long finalAutoSize = autoUpdateSize;
                final String finalFallbackUrl = fallbackFullUrl;
                final String finalFallbackName = fallbackFullFileName;

                if (isNewerVersion(tagName, CURRENT_VERSION)) {
                    if (silentIfUpToDate && !localBinaries.isEmpty()) {
                        // Silent background pre-download & staging (VS Code / Chrome style)
                        stageUpdateInBackground(parent, tagName, releaseName, body, htmlUrl,
                                finalManualUrl, finalManualName, finalManualSize,
                                finalAutoUrl, finalAutoName, finalAutoSize,
                                finalFallbackUrl, finalFallbackName);
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            showUpdateDialog(parent, tagName, releaseName, body, htmlUrl,
                                    finalManualUrl, finalManualName, finalManualSize,
                                    finalAutoUrl, finalAutoName, finalAutoSize,
                                    finalFallbackUrl, finalFallbackName);
                        });
                    }
                } else {
                    if (!silentIfUpToDate) {
                        SwingUtilities.invokeLater(() -> {
                            Object[] options = {"🌐 View on GitHub", "OK"};
                            int choice = JOptionPane.showOptionDialog(parent,
                                "✅ You're up to date!\n\nSchemaSync v" + CURRENT_VERSION + " is the latest version available.\n\nLatest GitHub Release: " + tagName,
                                "SchemaSync Up to Date",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.INFORMATION_MESSAGE,
                                null, options, options[1]);
                            if (choice == 0) {
                                try {
                                    Desktop.getDesktop().browse(new URI(htmlUrl));
                                } catch (Exception ignored) {}
                            }
                        });
                    }
                }

            } catch (Exception ex) {
                if (!silentIfUpToDate) {
                    SwingUtilities.invokeLater(() -> {
                        Object[] options = {"🌐 Open GitHub Releases", "OK"};
                        int choice = JOptionPane.showOptionDialog(parent,
                            "Could not connect to GitHub API:\n" + ex.getMessage() + "\n\nWould you like to open the GitHub releases page in your browser?",
                            "Update Check",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE,
                            null, options, options[1]);
                        if (choice == 0) {
                            try {
                                Desktop.getDesktop().browse(new URI("https://github.com/" + GITHUB_REPO + "/releases"));
                            } catch (Exception ignored) {}
                        }
                    });
                }
            }
        }, "SchemaSync-UpdateChecker").start();
    }

    /**
     * Silently downloads the update (~1.2 MB delta patch or JAR) in the background,
     * pre-stages the patched binary on disk, and lights up the toolbar pill button
     * ("✨ Update Ready (vX.Y.Z) — Restart") so the user can update in 1 second with zero wait.
     */
    private static void stageUpdateInBackground(Component parent, String tagName, String releaseName,
                                                String body, String htmlUrl,
                                                String manualUrl, String manualName, long manualSize,
                                                String autoUrl, String autoName, long autoSize,
                                                String fallbackUrl, String fallbackName) {
        JButton btn = toolbarUpdateButton;
        if (btn != null) {
            SwingUtilities.invokeLater(() -> {
                btn.setText("⏳ Downloading " + tagName + "...");
                btn.setToolTipText("Silently downloading SchemaSync " + tagName + " update in the background...");
            });
        }

        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "SchemaSyncUpdate");
            if (!tempDir.exists()) tempDir.mkdirs();
            File downloaded = new File(tempDir, autoName);

            downloadFileWithRedirects(autoUrl, downloaded, null, null);
            Map<File, File> stagedMap = prepareStagedBinaries(downloaded, fallbackUrl, fallbackName, tempDir);

            if (!stagedMap.isEmpty()) {
                stagedVersionTag = tagName;
                preStagedReplacements = stagedMap;

                SwingUtilities.invokeLater(() -> {
                    if (toolbarUpdateButton != null) {
                        toolbarUpdateButton.setText("✨ Update Ready (" + tagName + ") — Restart");
                        toolbarUpdateButton.setBackground(new Color(16, 185, 129));
                        toolbarUpdateButton.setForeground(Color.WHITE);
                        toolbarUpdateButton.setFont(toolbarUpdateButton.getFont().deriveFont(Font.BOLD));
                        toolbarUpdateButton.setToolTipText("SchemaSync " + tagName + " is already downloaded and patched! Click to restart in 1 second.");
                    }
                });
                return;
            }
        } catch (Exception ignored) {
            // Fall back to showing normal update button state if silent pre-staging fails
        }

        SwingUtilities.invokeLater(() -> {
            if (toolbarUpdateButton != null) {
                toolbarUpdateButton.setText("🚀 Update " + tagName);
                toolbarUpdateButton.setBackground(new Color(16, 185, 129));
                toolbarUpdateButton.setForeground(Color.WHITE);
                toolbarUpdateButton.setToolTipText("New SchemaSync update " + tagName + " available! Click to update.");
            }
        });
    }

    public static boolean isNewerVersion(String remote, String current) {
        if (remote == null || remote.trim().isEmpty()) return false;
        if (current == null || current.trim().isEmpty()) return true;

        String rClean = remote.trim().replaceAll("^[vV]", "").split("[-_]")[0];
        String cClean = current.trim().replaceAll("^[vV]", "").split("[-_]")[0];

        String[] rParts = rClean.split("\\.");
        String[] cParts = cClean.split("\\.");

        int maxLen = Math.max(rParts.length, cParts.length);
        for (int i = 0; i < maxLen; i++) {
            int rVal = 0;
            if (i < rParts.length) {
                try { rVal = Integer.parseInt(rParts[i].replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
            }
            int cVal = 0;
            if (i < cParts.length) {
                try { cVal = Integer.parseInt(cParts[i].replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
            }
            if (rVal > cVal) return true;
            if (rVal < cVal) return false;
        }
        return false;
    }

    /**
     * Detects and repairs UTF-8 strings that were accidentally double-encoded through
     * Windows-1252 / ISO-8859-1 (e.g., "ðŸŽ›ï¸\uFFFD â–²" -> "🎛️ ▲", "âš¡" -> "⚡", "â€”" -> "—").
     */
    public static String repairMojibake(String text) {
        if (text == null || text.isEmpty()) return text;
        if (!text.contains("ðŸ") && !text.contains("â") && !text.contains("Ã") && !text.contains("ï¸")) {
            return text;
        }
        try {
            String patched = text
                    .replace("ï¸\uFFFD", "ï¸\u008F")
                    .replace("â—\uFFFD", "â—\u008F")
                    .replace("ðŸ“\uFFFD", "ðŸ“\u0090")
                    .replace("ðŸ”\uFFFD", "ðŸ”\u008D");

            java.nio.charset.Charset cp1252 = java.nio.charset.Charset.forName("windows-1252");
            ByteArrayOutputStream bos = new ByteArrayOutputStream(patched.length() * 2);
            for (int i = 0; i < patched.length(); i++) {
                char c = patched.charAt(i);
                if (c == 0x81 || c == 0x8D || c == 0x8F || c == 0x90 || c == 0x9D) {
                    bos.write((byte) c);
                } else {
                    String single = String.valueOf(c);
                    if (cp1252.newEncoder().canEncode(single)) {
                        byte[] encoded = single.getBytes(cp1252);
                        bos.write(encoded, 0, encoded.length);
                    } else {
                        byte[] utf8 = single.getBytes(StandardCharsets.UTF_8);
                        bos.write(utf8, 0, utf8.length);
                    }
                }
            }
            String decoded = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            return decoded.replace("\uFFFD", "");
        } catch (Exception ignored) {
            return text;
        }
    }

    /**
     * Repairs character encoding artifacts and strips raw Markdown syntax markers
     * so release notes render cleanly inside the Update Dialog changelog view.
     */
    public static String formatReleaseNotesForDisplay(String rawBody) {
        if (rawBody == null || rawBody.isEmpty()) return "No release notes provided.";
        String repaired = repairMojibake(rawBody);
        String[] lines = repaired.replace("\r\n", "\n").split("\n");
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("---")) {
                out.append("────────────────────────────────────────────────────────\n");
                continue;
            }
            String cleaned = line
                    .replaceFirst("^#{1,6}\\s+", "")
                    .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                    .replaceAll("`([^`]+)`", "$1");
            out.append(cleaned).append("\n");
        }
        return out.toString().trim();
    }

    private static void showUpdateDialog(Component parent, String tagName, String releaseName,
                                         String body, String htmlUrl, String downloadUrl,
                                         String downloadFileName, long packageSize,
                                         String autoUpdateUrl, String autoUpdateFileName, long autoUpdateSize,
                                         String fallbackFullUrl, String fallbackFullName) {
        Frame owner = parent instanceof Frame ? (Frame) parent : (Frame) SwingUtilities.getWindowAncestor(parent);
        JDialog dlg = new JDialog(owner, "🚀 SchemaSync Instant Update: " + tagName, true);
        dlg.setSize(660, 520);
        dlg.setLocationRelativeTo(parent);
        dlg.setLayout(new BorderLayout(10, 10));

        boolean isAlreadyStaged = tagName.equals(stagedVersionTag)
                && preStagedReplacements != null
                && !preStagedReplacements.isEmpty();
        boolean isDeltaPatch = autoUpdateFileName != null && autoUpdateFileName.toLowerCase().contains("patch");

        // Header
        JPanel header = new JPanel(new GridLayout(3, 1, 2, 2));
        header.setBorder(BorderFactory.createEmptyBorder(14, 18, 6, 18));
        JLabel title = new JLabel(isAlreadyStaged
                ? "✨ SchemaSync " + tagName + " is downloaded and ready to apply!"
                : "A new version of SchemaSync is available!");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setForeground(new Color(16, 185, 129));

        JLabel sub = new JLabel("Installed: v" + CURRENT_VERSION + "  ➔  Available: " + tagName + " (" + releaseName + ")");
        sub.setFont(sub.getFont().deriveFont(Font.PLAIN, 13f));

        String modeDesc;
        if (isAlreadyStaged) {
            modeDesc = "⚡ Instant Hot-Swap Ready (0 MB download • 1-second restart)";
        } else if (isDeltaPatch) {
            String patchSz = autoUpdateSize > 0 ? formatFileSize(autoUpdateSize) : "~1.2 MB";
            modeDesc = "⚡ Fast Delta Patch (" + patchSz + " in-place code update — no full reinstall needed)";
        } else {
            String pkgSz = autoUpdateSize > 0 ? formatFileSize(autoUpdateSize) : (packageSize > 0 ? formatFileSize(packageSize) : "");
            modeDesc = "In-Place Package Update" + (!pkgSz.isEmpty() ? " (" + pkgSz + ")" : "");
        }
        JLabel sizeLbl = new JLabel(modeDesc);
        sizeLbl.setForeground(Color.GRAY);
        sizeLbl.setFont(sizeLbl.getFont().deriveFont(Font.ITALIC, 11f));

        header.add(title);
        header.add(sub);
        header.add(sizeLbl);
        dlg.add(header, BorderLayout.NORTH);

        // Changelog text area
        JTextArea notesArea = new JTextArea(formatReleaseNotesForDisplay(body));
        notesArea.setEditable(false);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setFont(ThemeManager.getUiFont(Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(notesArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Release Notes / Changelog"));
        dlg.add(scroll, BorderLayout.CENTER);

        // South Panel: Progress area + Buttons
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.setBorder(BorderFactory.createEmptyBorder(6, 16, 12, 16));

        JPanel progressSection = new JPanel(new BorderLayout(6, 6));
        progressSection.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        JLabel statusLabel = new JLabel(isAlreadyStaged
                ? "✅ Update is already staged on disk — click the green button below for a 1-second restart."
                : "Click the green button below for an in-place update without reinstalling from scratch.");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        JCheckBox silentRestartBox = new JCheckBox("Automatically close & restart SchemaSync after applying update", true);
        silentRestartBox.setFont(silentRestartBox.getFont().deriveFont(Font.PLAIN, 11f));

        JPanel progSub = new JPanel(new BorderLayout(4, 4));
        progSub.add(statusLabel, BorderLayout.NORTH);
        progSub.add(progressBar, BorderLayout.CENTER);
        progSub.add(silentRestartBox, BorderLayout.SOUTH);
        progressSection.add(progSub, BorderLayout.CENTER);

        southPanel.add(progressSection);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));

        String primaryBtnText = isAlreadyStaged
                ? "⚡ Apply Ready Update & Restart (1s)"
                : (isDeltaPatch ? "⚡ Instant Patch & Restart (" + (autoUpdateSize > 0 ? formatFileSize(autoUpdateSize) : "~1.2 MB") + ")"
                                : "⚡ 1-Click Update & Restart");
        JButton updateNowBtn = new JButton(primaryBtnText);
        updateNowBtn.setFont(updateNowBtn.getFont().deriveFont(Font.BOLD, 12f));
        updateNowBtn.setToolTipText("Updates SchemaSync in-place without running the installer wizard and restarts immediately");
        updateNowBtn.setBackground(new Color(16, 185, 129));
        updateNowBtn.setForeground(Color.WHITE);

        JButton downloadInstallerBtn = new JButton("📥 Full Installer");
        downloadInstallerBtn.setToolTipText("Open direct download link in browser if you prefer the full standalone installer");
        downloadInstallerBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(downloadUrl));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, "Error opening installer download link: " + ex.getMessage(), "Download Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton viewGithubBtn = new JButton("🌐 View on GitHub");
        viewGithubBtn.setToolTipText("Open the full GitHub release details in your browser");
        viewGithubBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(htmlUrl));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, "Error opening GitHub link: " + ex.getMessage(), "Browser Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton laterBtn = new JButton("Later");
        AtomicBoolean isDownloading = new AtomicBoolean(false);
        AtomicBoolean cancelRequested = new AtomicBoolean(false);

        updateNowBtn.addActionListener(e -> {
            if (isDownloading.get()) return;
            isDownloading.set(true);
            cancelRequested.set(false);

            updateNowBtn.setEnabled(false);
            downloadInstallerBtn.setEnabled(false);
            viewGithubBtn.setEnabled(false);
            laterBtn.setText("Cancel");

            progressBar.setValue(0);
            progressBar.setVisible(true);

            new Thread(() -> {
                File tempInstaller = null;
                try {
                    boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");

                    // Fast path: if background thread already staged the patched binaries, swap & restart immediately!
                    if (tagName.equals(stagedVersionTag) && preStagedReplacements != null && !preStagedReplacements.isEmpty()) {
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setIndeterminate(true);
                            statusLabel.setText("⚡ Applying pre-staged update and restarting SchemaSync...");
                        });
                        applyStagedMapAndRestart(preStagedReplacements, tagName, silentRestartBox.isSelected(), dlg);
                        return;
                    }

                    File tempDir = new File(System.getProperty("java.io.tmpdir"), "SchemaSyncUpdate");
                    if (!tempDir.exists()) tempDir.mkdirs();
                    tempInstaller = new File(tempDir, autoUpdateFileName);

                    statusLabel.setText("Connecting to GitHub release server...");
                    downloadFileWithRedirects(autoUpdateUrl, tempInstaller, cancelRequested, (curDown, totalLen, mbps) -> {
                        int percent = totalLen > 0 ? (int) ((curDown * 100) / totalLen) : -1;
                        SwingUtilities.invokeLater(() -> {
                            if (percent >= 0) {
                                progressBar.setIndeterminate(false);
                                progressBar.setValue(percent);
                                statusLabel.setText(String.format("Downloading %s: %s / %s (%d%%) • %.1f MB/s",
                                        isDeltaPatch ? "delta patch" : "update",
                                        formatFileSize(curDown), formatFileSize(totalLen), percent, mbps));
                            } else {
                                progressBar.setIndeterminate(true);
                                statusLabel.setText(String.format("Downloading: %s • %.1f MB/s",
                                        formatFileSize(curDown), mbps));
                            }
                        });
                    });

                    if (cancelRequested.get()) {
                        if (tempInstaller.exists()) tempInstaller.delete();
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Update cancelled.");
                            progressBar.setVisible(false);
                            updateNowBtn.setEnabled(true);
                            downloadInstallerBtn.setEnabled(true);
                            viewGithubBtn.setEnabled(true);
                            laterBtn.setText("Later");
                            isDownloading.set(false);
                        });
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        progressBar.setIndeterminate(true);
                        statusLabel.setText("⚡ Patching local installation in-place and restarting...");
                    });

                    // Try in-place delta patch / staged swap first
                    Map<File, File> stagedMap = prepareStagedBinaries(tempInstaller, fallbackFullUrl, fallbackFullName, tempDir);
                    if (!stagedMap.isEmpty()) {
                        applyStagedMapAndRestart(stagedMap, tagName, silentRestartBox.isSelected(), dlg);
                        return;
                    }

                    // Legacy fallback if no local writable binaries were matched
                    if (isWin) {
                        installAndRestartOnWindows(tempInstaller, tagName, silentRestartBox.isSelected(), dlg);
                    } else {
                        installAndRestartOnLinux(tempInstaller, tagName, silentRestartBox.isSelected(), dlg);
                    }

                } catch (Exception ex) {
                    if (tempInstaller != null && tempInstaller.exists()) {
                        tempInstaller.delete();
                    }
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Update failed: " + ex.getMessage());
                        progressBar.setIndeterminate(false);
                        progressBar.setVisible(false);
                        updateNowBtn.setEnabled(true);
                        downloadInstallerBtn.setEnabled(true);
                        viewGithubBtn.setEnabled(true);
                        laterBtn.setText("Later");
                        isDownloading.set(false);

                        JOptionPane.showMessageDialog(dlg,
                            "Unable to complete 1-Click Update:\n" + ex.getMessage() +
                            "\n\nYou can still download the package manually using 'Full Installer'.",
                            "Update Error", JOptionPane.WARNING_MESSAGE);
                    });
                }
            }, "SchemaSync-InstantUpdater").start();
        });

        laterBtn.addActionListener(e -> {
            if (isDownloading.get()) {
                cancelRequested.set(true);
            } else {
                dlg.dispose();
            }
        });

        btnPanel.add(updateNowBtn);
        btnPanel.add(downloadInstallerBtn);
        btnPanel.add(viewGithubBtn);
        btnPanel.add(laterBtn);

        southPanel.add(btnPanel);
        dlg.add(southPanel, BorderLayout.SOUTH);

        dlg.setVisible(true);
    }

    private interface DownloadProgressCallback {
        void onProgress(long downloadedBytes, long totalBytes, double mbPerSec);
    }

    private static void downloadFileWithRedirects(String urlStr, File destFile,
                                                  AtomicBoolean cancelRequested,
                                                  DownloadProgressCallback progressCallback) throws Exception {
        URL curUrl = new URI(urlStr).toURL();
        HttpURLConnection conn = null;
        int redirectCount = 0;
        while (redirectCount < 8) {
            conn = (HttpURLConnection) curUrl.openConnection();
            conn.setRequestProperty("User-Agent", "SchemaSync/" + CURRENT_VERSION);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308) {
                String newLoc = conn.getHeaderField("Location");
                conn.disconnect();
                if (newLoc == null || newLoc.isEmpty()) break;
                curUrl = new URI(newLoc).toURL();
                redirectCount++;
            } else {
                break;
            }
        }

        if (conn == null || conn.getResponseCode() != 200) {
            int code = conn != null ? conn.getResponseCode() : -1;
            throw new Exception("HTTP server returned error code " + code);
        }

        long totalLength = conn.getContentLengthLong();
        try (InputStream in = new BufferedInputStream(conn.getInputStream(), 65536);
             FileOutputStream out = new FileOutputStream(destFile)) {

            byte[] buffer = new byte[32768];
            long downloaded = 0;
            int read;
            long startTime = System.currentTimeMillis();
            long lastUiUpdate = startTime;

            while ((read = in.read(buffer)) != -1) {
                if (cancelRequested != null && cancelRequested.get()) {
                    return;
                }
                out.write(buffer, 0, read);
                downloaded += read;

                if (progressCallback != null) {
                    long now = System.currentTimeMillis();
                    if (now - lastUiUpdate > 100) {
                        double elapsed = (now - startTime) / 1000.0;
                        double mbps = elapsed > 0 ? (downloaded / (1024.0 * 1024.0)) / elapsed : 0;
                        progressCallback.onProgress(downloaded, totalLength, mbps);
                        lastUiUpdate = now;
                    }
                }
            }
        }
    }

    /**
     * Finds all local SchemaSync binaries (SchemaSync.jar and/or SchemaSync.exe) that belong to
     * the currently running installation so they can be patched in-place without running an installer.
     */
    public static List<File> resolveLocalPatchableBinaries() {
        Set<File> targets = new LinkedHashSet<>();

        File runningJar = getRunningJarFile();
        if (runningJar != null && runningJar.exists()) {
            targets.add(runningJar);
            File parent = runningJar.getParentFile();
            if (parent != null) {
                File siblingExe = new File(parent, "SchemaSync.exe");
                if (siblingExe.exists() && siblingExe.canWrite()) {
                    targets.add(siblingExe);
                }
            }
        }

        File runningExe = getRunningExeFile();
        if (runningExe != null && runningExe.exists()) {
            targets.add(runningExe);
            File parent = runningExe.getParentFile();
            if (parent != null) {
                File siblingJar = new File(parent, "SchemaSync.jar");
                if (siblingJar.exists() && siblingJar.canWrite()) {
                    targets.add(siblingJar);
                }
            }
        }

        boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWin && targets.isEmpty()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                File appDir = new File(localAppData, "Programs\\SchemaSync");
                File installedJar = new File(appDir, "SchemaSync.jar");
                File installedExe = new File(appDir, "SchemaSync.exe");
                if (installedJar.exists() && installedJar.canWrite()) targets.add(installedJar);
                if (installedExe.exists() && installedExe.canWrite()) targets.add(installedExe);
            }
        } else if (!isWin && targets.isEmpty()) {
            File userHome = new File(System.getProperty("user.home", "."));
            File userJar = new File(userHome, ".local/share/schemasync/SchemaSync.jar");
            if (userJar.exists() && userJar.canWrite()) {
                targets.add(userJar);
            } else {
                File sysJar = new File("/usr/share/schemasync/SchemaSync.jar");
                if (sysJar.exists()) targets.add(sysJar);
            }
        }

        return new ArrayList<>(targets);
    }

    /**
     * Builds staged replacement binaries in tempDir from either a lightweight delta patch JAR
     * (`SchemaSync-patch.jar`) or a full JAR/EXE.
     */
    private static Map<File, File> prepareStagedBinaries(File downloadedFile, String fallbackFullUrl,
                                                         String fallbackFullName, File tempDir) throws Exception {
        Map<File, File> stagedMap = new LinkedHashMap<>();
        List<File> targets = resolveLocalPatchableBinaries();
        if (targets.isEmpty()) return stagedMap;

        String lowerName = downloadedFile.getName().toLowerCase();
        boolean isPatch = lowerName.contains("patch") && lowerName.endsWith(".jar");

        if (isPatch) {
            for (File target : targets) {
                File staged = new File(tempDir, "staged_" + System.nanoTime() + "_" + target.getName());
                if (applyDeltaPatch(target, downloadedFile, staged)) {
                    stagedMap.put(target, staged);
                }
            }
            // If delta patching failed for any reason and we have a fallback full URL, download the full binary
            if (stagedMap.isEmpty() && fallbackFullUrl != null && !fallbackFullUrl.isEmpty()) {
                File fallbackFile = new File(tempDir, fallbackFullName != null ? fallbackFullName : "SchemaSync.jar");
                downloadFileWithRedirects(fallbackFullUrl, fallbackFile, null, null);
                downloadedFile = fallbackFile;
                lowerName = downloadedFile.getName().toLowerCase();
            } else {
                return stagedMap;
            }
        }

        // Full JAR or EXE replacement (still in-place without Setup wizard!)
        if (lowerName.endsWith(".jar") && !lowerName.contains("patch")) {
            for (File target : targets) {
                if (target.getName().toLowerCase().endsWith(".jar")) {
                    stagedMap.put(target, downloadedFile);
                } else if (target.getName().toLowerCase().endsWith(".exe")) {
                    // Preserve Launch4j .exe stub and replace its embedded JAR payload!
                    File stagedExe = new File(tempDir, "staged_" + System.nanoTime() + "_" + target.getName());
                    if (replaceExePayloadWithJar(target, downloadedFile, stagedExe)) {
                        stagedMap.put(target, stagedExe);
                    }
                }
            }
        } else if (lowerName.endsWith(".exe") && !lowerName.contains("setup")) {
            for (File target : targets) {
                if (target.getName().toLowerCase().endsWith(".exe")) {
                    stagedMap.put(target, downloadedFile);
                }
            }
        }

        return stagedMap;
    }

    /**
     * Locates the byte offset where the embedded ZIP/JAR archive begins inside a file.
     * Returns 0 for standard .jar files, or the Launch4j PE header length (e.g. 115,712) for .exe wrappers.
     */
    public static long findEmbeddedZipOffset(File binaryFile) throws Exception {
        if (binaryFile == null || !binaryFile.exists() || binaryFile.length() < 32) {
            return -1L;
        }
        try (RandomAccessFile raf = new RandomAccessFile(binaryFile, "r")) {
            byte[] head = new byte[4];
            raf.readFully(head);
            // Standard ZIP/JAR starts immediately with PK\x03\x04
            if (head[0] == 0x50 && head[1] == 0x4B && head[2] == 0x03 && head[3] == 0x04) {
                return 0L;
            }

            // Scan first 2 MB for Launch4j embedded JAR start: PK\x03\x04 where entry name starts with META-INF/ or com/
            long maxScan = Math.min(binaryFile.length() - 40, 2L * 1024L * 1024L);
            raf.seek(0);
            byte[] buf = new byte[(int) maxScan + 40];
            int n = raf.read(buf);
            for (int i = 0; i < n - 38; i++) {
                if (buf[i] == 0x50 && buf[i + 1] == 0x4B && buf[i + 2] == 0x03 && buf[i + 3] == 0x04) {
                    int nameLen = (buf[i + 26] & 0xFF) | ((buf[i + 27] & 0xFF) << 8);
                    if (nameLen >= 4 && nameLen < 200 && i + 30 + nameLen <= n) {
                        String entryName = new String(buf, i + 30, nameLen, StandardCharsets.UTF_8);
                        if (entryName.startsWith("META-INF/") || entryName.startsWith("com/") || entryName.startsWith("icons/")) {
                            return i;
                        }
                    }
                }
            }
        }
        return -1L;
    }

    /**
     * Applies a lightweight delta patch JAR (`SchemaSync-patch.jar`, ~1.2 MB) to a local
     * `SchemaSync.jar` or `SchemaSync.exe` (Launch4j wrapper), writing the updated binary to `stagedOutput`.
     *
     * Preserves:
     * - Any native Launch4j .exe PE prefix header (bytes 0 .. zipOffset)
     * - The shaded META-INF/MANIFEST.MF (Main-Class: com.dbtool.Main)
     * - All 56 MB of third-party dependency libraries in the base binary
     * Replaces:
     * - All `com/dbtool/**` classes and application resources with the new versions from `patchJar`.
     */
    public static boolean applyDeltaPatch(File baseBinary, File patchJar, File stagedOutput) {
        if (baseBinary == null || !baseBinary.exists() || patchJar == null || !patchJar.exists()) {
            return false;
        }
        File tempOut = new File(stagedOutput.getParentFile(), stagedOutput.getName() + ".tmp");
        try {
            long zipOffset = findEmbeddedZipOffset(baseBinary);
            if (zipOffset < 0) return false;

            // 1. Read all entries from the lightweight patchJar (~1.2 MB) into memory
            Map<String, byte[]> patchEntries = new LinkedHashMap<>();
            byte[] patchManifest = null;
            try (ZipInputStream patchZis = new ZipInputStream(new BufferedInputStream(new FileInputStream(patchJar)))) {
                ZipEntry pe;
                byte[] buf = new byte[16384];
                while ((pe = patchZis.getNextEntry()) != null) {
                    if (pe.isDirectory()) continue;
                    String name = pe.getName();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int r;
                    while ((r = patchZis.read(buf)) != -1) {
                        baos.write(buf, 0, r);
                    }
                    if ("META-INF/MANIFEST.MF".equalsIgnoreCase(name)) {
                        patchManifest = baos.toByteArray();
                    } else if (!name.startsWith("META-INF/maven/")) {
                        patchEntries.put(name, baos.toByteArray());
                    }
                }
            }

            if (!patchEntries.containsKey("com/dbtool/Main.class")) {
                return false;
            }

            // 2. Write optional PE stub prefix (for SchemaSync.exe) + merged ZIP stream
            try (FileOutputStream fos = new FileOutputStream(tempOut);
                 BufferedOutputStream bos = new BufferedOutputStream(fos, 65536)) {

                if (zipOffset > 0) {
                    try (FileInputStream baseFis = new FileInputStream(baseBinary)) {
                        byte[] prefixBuf = new byte[32768];
                        long remaining = zipOffset;
                        while (remaining > 0) {
                            int toRead = (int) Math.min(prefixBuf.length, remaining);
                            int r = baseFis.read(prefixBuf, 0, toRead);
                            if (r <= 0) break;
                            bos.write(prefixBuf, 0, r);
                            remaining -= r;
                        }
                    }
                }

                ZipOutputStream zos = new ZipOutputStream(bos);
                Set<String> writtenNames = new LinkedHashSet<>();

                // Stream baseBinary entries from zipOffset
                try (FileInputStream baseFis = new FileInputStream(baseBinary)) {
                    long skipped = 0;
                    while (skipped < zipOffset) {
                        long s = baseFis.skip(zipOffset - skipped);
                        if (s <= 0) break;
                        skipped += s;
                    }

                    try (ZipInputStream baseZis = new ZipInputStream(new BufferedInputStream(baseFis, 65536))) {
                        ZipEntry be;
                        byte[] buf = new byte[32768];
                        while ((be = baseZis.getNextEntry()) != null) {
                            if (be.isDirectory()) continue;
                            String name = be.getName();
                            if (writtenNames.contains(name)) continue;

                            // Preserve shaded MANIFEST.MF from baseBinary first
                            if ("META-INF/MANIFEST.MF".equalsIgnoreCase(name)) {
                                ZipEntry outEntry = new ZipEntry(name);
                                zos.putNextEntry(outEntry);
                                int r;
                                while ((r = baseZis.read(buf)) != -1) {
                                    zos.write(buf, 0, r);
                                }
                                zos.closeEntry();
                                writtenNames.add(name);
                                continue;
                            }

                            // Skip old com/dbtool/** classes or resources that are replaced by patchEntries
                            if (name.startsWith("com/dbtool/") || patchEntries.containsKey(name)) {
                                continue;
                            }

                            // Copy all 3rd-party dependency entries straight through
                            ZipEntry outEntry = new ZipEntry(name);
                            zos.putNextEntry(outEntry);
                            int r;
                            while ((r = baseZis.read(buf)) != -1) {
                                zos.write(buf, 0, r);
                            }
                            zos.closeEntry();
                            writtenNames.add(name);
                        }
                    }
                }

                // If baseBinary had no MANIFEST.MF, write one with Main-Class: com.dbtool.Main
                if (!writtenNames.contains("META-INF/MANIFEST.MF")) {
                    byte[] mfBytes = (patchManifest != null && new String(patchManifest, StandardCharsets.UTF_8).contains("Main-Class"))
                            ? patchManifest
                            : "Manifest-Version: 1.0\r\nMain-Class: com.dbtool.Main\r\n\r\n".getBytes(StandardCharsets.UTF_8);
                    zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
                    zos.write(mfBytes);
                    zos.closeEntry();
                    writtenNames.add("META-INF/MANIFEST.MF");
                }

                // Write all updated SchemaSync classes & resources from patchEntries
                for (Map.Entry<String, byte[]> entry : patchEntries.entrySet()) {
                    String name = entry.getKey();
                    if (writtenNames.contains(name)) continue;
                    zos.putNextEntry(new ZipEntry(name));
                    zos.write(entry.getValue());
                    zos.closeEntry();
                    writtenNames.add(name);
                }

                zos.finish();
                zos.flush();
            }

            if (!tempOut.exists() || tempOut.length() <= patchJar.length()) {
                tempOut.delete();
                return false;
            }

            Files.move(tempOut.toPath(), stagedOutput.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            if (tempOut.exists()) tempOut.delete();
            return false;
        }
    }

    /**
     * Replaces the embedded JAR payload of a Launch4j .exe wrapper with a new full JAR.
     */
    private static boolean replaceExePayloadWithJar(File baseExe, File newJar, File stagedExe) {
        try {
            long zipOffset = findEmbeddedZipOffset(baseExe);
            if (zipOffset <= 0) return false;
            try (FileOutputStream fos = new FileOutputStream(stagedExe);
                 BufferedOutputStream bos = new BufferedOutputStream(fos, 65536);
                 FileInputStream exeFis = new FileInputStream(baseExe);
                 FileInputStream jarFis = new FileInputStream(newJar)) {

                byte[] buf = new byte[32768];
                long remaining = zipOffset;
                while (remaining > 0) {
                    int r = exeFis.read(buf, 0, (int) Math.min(buf.length, remaining));
                    if (r <= 0) break;
                    bos.write(buf, 0, r);
                    remaining -= r;
                }
                int r;
                while ((r = jarFis.read(buf)) != -1) {
                    bos.write(buf, 0, r);
                }
            }
            return stagedExe.exists() && stagedExe.length() > newJar.length();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Swaps the pre-staged updated binary(s) into place and restarts SchemaSync in ~1 second
     * without launching any installer wizard.
     */
    private static void applyStagedMapAndRestart(Map<File, File> stagedMap, String tagName,
                                                 boolean autoRestart, JDialog dlg) throws Exception {
        boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "SchemaSyncUpdate");
        if (!tempDir.exists()) tempDir.mkdirs();

        if (isWin) {
            File updateBatch = new File(tempDir, "instant_update_restart.bat");
            StringBuilder batch = new StringBuilder();
            batch.append("@echo off\r\n");
            batch.append("setlocal enabledelayedexpansion\r\n");
            batch.append("timeout /t 1 /nobreak >nul\r\n");

            File primaryLaunchTarget = null;
            for (Map.Entry<File, File> entry : stagedMap.entrySet()) {
                File target = entry.getKey();
                File staged = entry.getValue();
                if (primaryLaunchTarget == null || target.getName().toLowerCase().endsWith(".jar")) {
                    primaryLaunchTarget = target;
                }
                batch.append("set RETRIES=0\r\n");
                batch.append(":retry_").append(Math.abs(target.hashCode())).append("\r\n");
                batch.append("copy /y \"").append(staged.getAbsolutePath()).append("\" \"").append(target.getAbsolutePath()).append("\" >nul 2>&1\r\n");
                batch.append("if errorlevel 1 (\r\n");
                batch.append("    timeout /t 1 /nobreak >nul\r\n");
                batch.append("    set /a RETRIES+=1\r\n");
                batch.append("    if !RETRIES! lss 15 goto retry_").append(Math.abs(target.hashCode())).append("\r\n");
                batch.append(")\r\n");
            }

            if (autoRestart && primaryLaunchTarget != null) {
                if (primaryLaunchTarget.getName().toLowerCase().endsWith(".jar")) {
                    String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "javaw.exe";
                    if (!new File(javaBin).exists()) {
                        javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java.exe";
                    }
                    if (!new File(javaBin).exists()) javaBin = "javaw";
                    batch.append("start \"\" \"").append(javaBin).append("\" -Duser.timezone=UTC -jar \"")
                         .append(primaryLaunchTarget.getAbsolutePath()).append("\"\r\n");
                } else {
                    batch.append("start \"\" \"").append(primaryLaunchTarget.getAbsolutePath()).append("\"\r\n");
                }
            }
            batch.append("del \"%~f0\" >nul 2>&1\r\n");

            Files.write(updateBatch.toPath(), batch.toString().getBytes(StandardCharsets.UTF_8));
            new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", "/min", updateBatch.getAbsolutePath()).start();
            Thread.sleep(300);
            System.exit(0);
        } else {
            // Linux / macOS in-place swap
            File userHome = new File(System.getProperty("user.home", "."));
            File userAppDir = new File(userHome, ".local/share/schemasync");
            if (!userAppDir.exists()) userAppDir.mkdirs();
            File userJar = new File(userAppDir, "SchemaSync.jar");
            File userBinDir = new File(userHome, ".local/bin");
            if (!userBinDir.exists()) userBinDir.mkdirs();
            File userLauncher = new File(userBinDir, "schemasync");

            File launchJar = null;
            for (Map.Entry<File, File> entry : stagedMap.entrySet()) {
                File target = entry.getKey();
                File staged = entry.getValue();
                if (target.canWrite() || (target.getParentFile() != null && target.getParentFile().canWrite())) {
                    Files.copy(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    launchJar = target;
                } else {
                    Files.copy(staged.toPath(), userJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    installUserLauncherAndDesktopEntry(userHome, userLauncher, userJar);
                    launchJar = userJar;
                }
            }

            if (!autoRestart) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(dlg,
                        "✅ SchemaSync has been updated to " + tagName + "!\nRestart the application to use the new version.",
                        "Update Complete", JOptionPane.INFORMATION_MESSAGE);
                    dlg.dispose();
                });
                return;
            }

            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            if (!new File(javaBin).exists()) javaBin = "java";
            File targetJar = launchJar != null ? launchJar : userJar;

            List<String> restartCmd = new ArrayList<>();
            restartCmd.add(javaBin);
            restartCmd.add("--add-opens");
            restartCmd.add("java.desktop/sun.awt.X11=ALL-UNNAMED");
            restartCmd.add("-Duser.timezone=UTC");
            restartCmd.add("-Dsun.java2d.pmoffscreen=false");
            restartCmd.add("-Dawt.useSystemAAFontSettings=on");
            restartCmd.add("-Dswing.aatext=true");
            restartCmd.add("-Dsun.java2d.uiScale.enabled=true");
            restartCmd.add("-Djdk.gtk.version=3");
            restartCmd.add("-jar");
            restartCmd.add(targetJar.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(restartCmd);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.start();
            Thread.sleep(300);
            System.exit(0);
        }
    }

    private static void installAndRestartOnWindows(File downloadedFile, String tagName, boolean autoRestart, JDialog dlg) throws Exception {
        File runningJar = getRunningJarFile();
        File runningExe = getRunningExeFile();
        String fileName = downloadedFile.getName().toLowerCase();
        File tempDir = downloadedFile.getParentFile();
        File updateBatch = new File(tempDir, "update_and_restart.bat");

        if (fileName.endsWith(".jar")) {
            File targetJar = (runningJar != null && runningJar.exists()) ? runningJar : downloadedFile;
            if (!autoRestart) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(dlg,
                        "✅ SchemaSync update downloaded!\nReplace your JAR with:\n" + downloadedFile.getAbsolutePath(),
                        "Update Ready", JOptionPane.INFORMATION_MESSAGE);
                    dlg.dispose();
                });
                return;
            }

            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "javaw.exe";
            if (!new File(javaBin).exists()) {
                javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java.exe";
            }
            if (!new File(javaBin).exists()) javaBin = "javaw";

            String batchContent = "@echo off\r\n"
                    + "setlocal enabledelayedexpansion\r\n"
                    + "timeout /t 1 /nobreak >nul\r\n"
                    + "set RETRIES=0\r\n"
                    + ":retry\r\n"
                    + "copy /y \"" + downloadedFile.getAbsolutePath() + "\" \"" + targetJar.getAbsolutePath() + "\" >nul 2>&1\r\n"
                    + "if errorlevel 1 (\r\n"
                    + "    timeout /t 1 /nobreak >nul\r\n"
                    + "    set /a RETRIES+=1\r\n"
                    + "    if !RETRIES! lss 15 goto retry\r\n"
                    + ")\r\n"
                    + "start \"\" \"" + javaBin + "\" -Duser.timezone=UTC -jar \"" + targetJar.getAbsolutePath() + "\"\r\n"
                    + "del \"%~f0\" >nul 2>&1\r\n";

            Files.write(updateBatch.toPath(), batchContent.getBytes(StandardCharsets.UTF_8));
            new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", "/min", updateBatch.getAbsolutePath()).start();
            Thread.sleep(400);
            System.exit(0);

        } else if (fileName.contains("setup") && fileName.endsWith(".exe")) {
            if (!autoRestart) {
                new ProcessBuilder(downloadedFile.getAbsolutePath()).start();
                dlg.dispose();
                return;
            }

            File targetExe = runningExe;
            if (targetExe == null || !targetExe.exists()) {
                String localAppData = System.getenv("LOCALAPPDATA");
                if (localAppData != null) {
                    File candidate = new File(localAppData, "Programs\\SchemaSync\\SchemaSync.exe");
                    if (candidate.exists()) targetExe = candidate;
                }
            }
            if (targetExe == null || !targetExe.exists()) {
                String programFiles = System.getenv("ProgramFiles");
                if (programFiles != null) {
                    File candidate = new File(programFiles, "SchemaSync\\SchemaSync.exe");
                    if (candidate.exists()) targetExe = candidate;
                }
            }

            String targetExePath = targetExe != null ? targetExe.getAbsolutePath() : "";

            String batchContent = "@echo off\r\n"
                    + "setlocal enabledelayedexpansion\r\n"
                    + "timeout /t 1 /nobreak >nul\r\n"
                    + "start /wait \"\" \"" + downloadedFile.getAbsolutePath() + "\" /SILENT /FORCECLOSEAPPLICATIONS /SUPPRESSMSGBOXES\r\n"
                    + "timeout /t 1 /nobreak >nul\r\n"
                    + "set TARGET=\"" + targetExePath + "\"\r\n"
                    + "if exist !TARGET! (\r\n"
                    + "    start \"\" !TARGET!\r\n"
                    + ") else if exist \"%LOCALAPPDATA%\\Programs\\SchemaSync\\SchemaSync.exe\" (\r\n"
                    + "    start \"\" \"%LOCALAPPDATA%\\Programs\\SchemaSync\\SchemaSync.exe\"\r\n"
                    + ") else if exist \"%ProgramFiles%\\SchemaSync\\SchemaSync.exe\" (\r\n"
                    + "    start \"\" \"%ProgramFiles%\\SchemaSync\\SchemaSync.exe\"\r\n"
                    + ")\r\n"
                    + "del \"%~f0\" >nul 2>&1\r\n";

            Files.write(updateBatch.toPath(), batchContent.getBytes(StandardCharsets.UTF_8));
            new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", "/min", updateBatch.getAbsolutePath()).start();
            Thread.sleep(400);
            System.exit(0);

        } else if (fileName.endsWith(".exe")) {
            File targetExe = runningExe != null ? runningExe : downloadedFile;
            if (!autoRestart) {
                new ProcessBuilder(downloadedFile.getAbsolutePath()).start();
                dlg.dispose();
                return;
            }

            String batchContent = "@echo off\r\n"
                    + "setlocal enabledelayedexpansion\r\n"
                    + "timeout /t 1 /nobreak >nul\r\n"
                    + "set RETRIES=0\r\n"
                    + ":retry\r\n"
                    + "copy /y \"" + downloadedFile.getAbsolutePath() + "\" \"" + targetExe.getAbsolutePath() + "\" >nul 2>&1\r\n"
                    + "if errorlevel 1 (\r\n"
                    + "    timeout /t 1 /nobreak >nul\r\n"
                    + "    set /a RETRIES+=1\r\n"
                    + "    if !RETRIES! lss 15 goto retry\r\n"
                    + ")\r\n"
                    + "start \"\" \"" + targetExe.getAbsolutePath() + "\"\r\n"
                    + "del \"%~f0\" >nul 2>&1\r\n";

            Files.write(updateBatch.toPath(), batchContent.getBytes(StandardCharsets.UTF_8));
            new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", "/min", updateBatch.getAbsolutePath()).start();
            Thread.sleep(400);
            System.exit(0);
        } else {
            throw new Exception("Unrecognized package format for Windows: " + fileName);
        }
    }

    private static void installAndRestartOnLinux(File downloadedFile, String tagName, boolean autoRestart, JDialog dlg) throws Exception {
        File runningJar = getRunningJarFile();
        File systemJar = new File("/usr/share/schemasync/SchemaSync.jar");
        File userHome = new File(System.getProperty("user.home", "."));
        File userAppDir = new File(userHome, ".local/share/schemasync");
        if (!userAppDir.exists()) userAppDir.mkdirs();
        File userJar = new File(userAppDir, "SchemaSync.jar");
        File userBinDir = new File(userHome, ".local/bin");
        if (!userBinDir.exists()) userBinDir.mkdirs();
        File userLauncher = new File(userBinDir, "schemasync");
        String fileName = downloadedFile.getName().toLowerCase();

        boolean updatedLocalJar = false;
        boolean updatedUserJar = false;
        boolean updatedSystemPkg = false;

        if (fileName.endsWith(".deb")) {
            updatedUserJar = extractJarFromDeb(downloadedFile, userJar);
            if (updatedUserJar) {
                installUserLauncherAndDesktopEntry(userHome, userLauncher, userJar);
            }
            if (runningJar != null && !runningJar.equals(systemJar) && !runningJar.equals(userJar) && runningJar.canWrite()) {
                updatedLocalJar = extractJarFromDeb(downloadedFile, runningJar);
            }
            if (systemJar.exists() && new File("/usr/bin/pkexec").exists() && new File("/usr/bin/dpkg").exists()) {
                try {
                    Process proc = new ProcessBuilder("pkexec", "dpkg", "-i", downloadedFile.getAbsolutePath())
                            .redirectErrorStream(true)
                            .start();
                    int exitCode = proc.waitFor();
                    if (exitCode == 0) updatedSystemPkg = true;
                } catch (Exception ignored) {}
            }
        } else if (fileName.endsWith(".jar")) {
            Files.copy(downloadedFile.toPath(), userJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            updatedUserJar = true;
            installUserLauncherAndDesktopEntry(userHome, userLauncher, userJar);
            if (runningJar != null && !runningJar.equals(userJar) && runningJar.canWrite()) {
                Files.copy(downloadedFile.toPath(), runningJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                updatedLocalJar = true;
            }
        } else if (fileName.endsWith(".rpm")) {
            if (new File("/usr/bin/pkexec").exists() && new File("/usr/bin/rpm").exists()) {
                try {
                    Process proc = new ProcessBuilder("pkexec", "rpm", "-Uvh", "--force", downloadedFile.getAbsolutePath())
                            .redirectErrorStream(true)
                            .start();
                    int exitCode = proc.waitFor();
                    if (exitCode == 0) updatedSystemPkg = true;
                } catch (Exception ignored) {}
            }
            installUserLauncherAndDesktopEntry(userHome, userLauncher, userJar.exists() ? userJar : systemJar);
        }

        if (!updatedUserJar && !updatedLocalJar && !updatedSystemPkg) {
            throw new Exception("Could not apply update automatically. Package saved at: " + downloadedFile.getAbsolutePath());
        }

        if (!autoRestart) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(dlg,
                    "✅ SchemaSync has been updated to " + tagName + "!\nRestart the application to use the new version.",
                    "Update Complete", JOptionPane.INFORMATION_MESSAGE);
                dlg.dispose();
            });
            return;
        }

        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        if (!new File(javaBin).exists()) javaBin = "java";

        File targetJar;
        if (updatedLocalJar && runningJar != null && runningJar.exists()) {
            targetJar = runningJar;
        } else if (updatedUserJar && userJar.exists()) {
            targetJar = userJar;
        } else if (updatedSystemPkg && systemJar.exists()) {
            targetJar = systemJar;
        } else {
            targetJar = (runningJar != null && runningJar.exists()) ? runningJar : userJar;
        }

        List<String> restartCmd = new ArrayList<>();
        if (userLauncher.exists() && userLauncher.canExecute()) {
            restartCmd.add(userLauncher.getAbsolutePath());
        } else {
            restartCmd.add(javaBin);
            restartCmd.add("--add-opens");
            restartCmd.add("java.desktop/sun.awt.X11=ALL-UNNAMED");
            restartCmd.add("-Duser.timezone=UTC");
            restartCmd.add("-Dsun.java2d.pmoffscreen=false");
            restartCmd.add("-Dawt.useSystemAAFontSettings=on");
            restartCmd.add("-Dswing.aatext=true");
            restartCmd.add("-Dsun.java2d.uiScale.enabled=true");
            restartCmd.add("-Djdk.gtk.version=3");
            restartCmd.add("-jar");
            restartCmd.add(targetJar.getAbsolutePath());
        }

        ProcessBuilder pb = new ProcessBuilder(restartCmd);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        pb.start();
        Thread.sleep(400);
        System.exit(0);
    }

    public static void ensureLinuxDesktopIntegration() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win") || os.contains("mac")) return;
        try {
            File userHome = new File(System.getProperty("user.home", "."));
            File hicolor512 = new File(userHome, ".local/share/icons/hicolor/512x512/apps/schemasync.png");
            File desktopFile = new File(userHome, ".local/share/applications/schemasync.desktop");
            if (!hicolor512.exists() || !desktopFile.exists()) {
                File userAppDir = new File(userHome, ".local/share/schemasync");
                if (!userAppDir.exists()) userAppDir.mkdirs();
                File userJar = new File(userAppDir, "SchemaSync.jar");
                File userBinDir = new File(userHome, ".local/bin");
                if (!userBinDir.exists()) userBinDir.mkdirs();
                File userLauncher = new File(userBinDir, "schemasync");
                installUserLauncherAndDesktopEntry(userHome, userLauncher, userJar);
            }
        } catch (Throwable ignored) {}
    }

    private static void installUserLauncherAndDesktopEntry(File userHome, File userLauncher, File userJar) {
        try {
            String script = "#!/bin/bash\n"
                    + "export _JAVA_AWT_WM_NONREPARENTING=1\n"
                    + "JAVA_OPTS=\"--add-opens java.desktop/sun.awt.X11=ALL-UNNAMED -Duser.timezone=UTC -Dsun.java2d.pmoffscreen=false -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true -Dsun.java2d.uiScale.enabled=true -Djdk.gtk.version=3\"\n"
                    + "USER_JAR=\"" + userJar.getAbsolutePath() + "\"\n"
                    + "SYS_JAR=\"/usr/share/schemasync/SchemaSync.jar\"\n"
                    + "if [ -f \"$USER_JAR\" ]; then\n"
                    + "    JAR_PATH=\"$USER_JAR\"\n"
                    + "else\n"
                    + "    JAR_PATH=\"$SYS_JAR\"\n"
                    + "fi\n"
                    + "exec java $JAVA_OPTS -jar \"$JAR_PATH\" \"$@\"\n";
            Files.write(userLauncher.toPath(), script.getBytes(StandardCharsets.UTF_8));
            userLauncher.setExecutable(true, false);

            File appsDir = new File(userHome, ".local/share/applications");
            if (!appsDir.exists()) appsDir.mkdirs();
            File desktopFile = new File(appsDir, "schemasync.desktop");
            String desktop = "[Desktop Entry]\n"
                    + "Version=1.0\n"
                    + "Type=Application\n"
                    + "Name=SchemaSync\n"
                    + "GenericName=Database Management Tool\n"
                    + "Comment=Modern, lightweight database management and synchronization tool\n"
                    + "Exec=" + userLauncher.getAbsolutePath() + " %F\n"
                    + "Icon=schemasync\n"
                    + "Terminal=false\n"
                    + "StartupNotify=true\n"
                    + "StartupWMClass=schemasync\n"
                    + "Categories=Development;Database;IDE;\n"
                    + "MimeType=application/sql;application/x-sqlite3;\n"
                    + "Keywords=database;sql;postgres;mysql;sqlite;schema;diff;sync;\n";
            Files.write(desktopFile.toPath(), desktop.getBytes(StandardCharsets.UTF_8));

            extractLinuxIcons(userHome);

            try { new ProcessBuilder("update-desktop-database", appsDir.getAbsolutePath()).start(); } catch (Exception ignored) {}
            try { new ProcessBuilder("gtk-update-icon-cache", "-f", "-t", new File(userHome, ".local/share/icons/hicolor").getAbsolutePath()).start(); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private static void extractLinuxIcons(File userHome) {
        try {
            File hicolor512 = new File(userHome, ".local/share/icons/hicolor/512x512/apps");
            File hicolor64 = new File(userHome, ".local/share/icons/hicolor/64x64/apps");
            File hicolor32 = new File(userHome, ".local/share/icons/hicolor/32x32/apps");
            File hicolor16 = new File(userHome, ".local/share/icons/hicolor/16x16/apps");
            File pixmaps = new File(userHome, ".local/share/pixmaps");
            hicolor512.mkdirs();
            hicolor64.mkdirs();
            hicolor32.mkdirs();
            hicolor16.mkdirs();
            pixmaps.mkdirs();

            extractResourceToFile("/icons/app-icon.png", new File(hicolor512, "schemasync.png"));
            extractResourceToFile("/icons/app-icon.png", new File(pixmaps, "schemasync.png"));
            extractResourceToFile("/icons/app-icon-64.png", new File(hicolor64, "schemasync.png"));
            extractResourceToFile("/icons/app-icon-32.png", new File(hicolor32, "schemasync.png"));
            extractResourceToFile("/icons/app-icon-16.png", new File(hicolor16, "schemasync.png"));
        } catch (Exception ignored) {}
    }

    private static void extractResourceToFile(String resourcePath, File target) {
        try (InputStream in = UpdateChecker.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {}
    }

    private static boolean isRunningFromExe() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return false;
        if (System.getProperty("launch4j.exefile") != null) return true;
        try {
            String cmd = ProcessHandle.current().info().command().orElse(null);
            if (cmd != null && cmd.toLowerCase().endsWith(".exe") &&
                !cmd.toLowerCase().endsWith("java.exe") &&
                !cmd.toLowerCase().endsWith("javaw.exe")) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static File getRunningExeFile() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return null;
        String l4j = System.getProperty("launch4j.exefile");
        if (l4j != null && !l4j.trim().isEmpty()) {
            File f = new File(l4j.trim());
            if (f.exists()) return f;
        }
        try {
            String cmd = ProcessHandle.current().info().command().orElse(null);
            if (cmd != null && cmd.toLowerCase().endsWith(".exe") &&
                !cmd.toLowerCase().endsWith("java.exe") &&
                !cmd.toLowerCase().endsWith("javaw.exe")) {
                File f = new File(cmd);
                if (f.exists()) return f;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static File getRunningJarFile() {
        try {
            URI uri = UpdateChecker.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            File f = new File(uri);
            if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
                return f;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean extractJarFromDeb(File debFile, File targetJar) {
        try (FileInputStream fis = new FileInputStream(debFile);
             org.apache.commons.compress.archivers.ar.ArArchiveInputStream arIn =
                     new org.apache.commons.compress.archivers.ar.ArArchiveInputStream(fis)) {
            org.apache.commons.compress.archivers.ar.ArArchiveEntry arEntry;
            while ((arEntry = arIn.getNextArEntry()) != null) {
                if (arEntry.getName().startsWith("data.tar.gz")) {
                    try (org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream gzIn =
                                 new org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream(arIn);
                         org.apache.commons.compress.archivers.tar.TarArchiveInputStream tarIn =
                                 new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(gzIn)) {
                        org.apache.commons.compress.archivers.tar.TarArchiveEntry tarEntry;
                        while ((tarEntry = tarIn.getNextTarEntry()) != null) {
                            if (tarEntry.getName().endsWith("usr/share/schemasync/SchemaSync.jar")) {
                                File tempOut = new File(targetJar.getParentFile(), targetJar.getName() + ".tmp");
                                try (FileOutputStream fos = new FileOutputStream(tempOut)) {
                                    byte[] buf = new byte[65536];
                                    int n;
                                    while ((n = tarIn.read(buf)) != -1) {
                                        fos.write(buf, 0, n);
                                    }
                                }
                                Files.move(
                                        tempOut.toPath(),
                                        targetJar.toPath(),
                                        StandardCopyOption.REPLACE_EXISTING);
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
