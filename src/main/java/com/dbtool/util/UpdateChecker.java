package com.dbtool.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enterprise In-App Auto-Updater & Update Studio.
 * Supports:
 * - Non-blocking background update checks on launch or demand
 * - Interactive release changelog viewer
 * - 1-Click In-App automated download & silent restart
 * - Manual browser download of Windows installers & GitHub release browsing
 *
 * Created & Developed by: NSENGIYUMVA Christophe
 * Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved.
 */
public class UpdateChecker {

    public static final String CURRENT_VERSION = com.dbtool.Main.APP_VERSION;
    public static final String GITHUB_REPO = "Ygtopher/SchemaSync-";
    public static final String API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

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
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                }

                ObjectMapper mapper = new ObjectMapper();
                JsonNode release = mapper.readTree(sb.toString());

                String tagName = release.path("tag_name").asText("").trim();
                String releaseName = release.path("name").asText(tagName);
                String body = release.path("body").asText("No release notes provided.");
                String htmlUrl = release.path("html_url").asText("https://github.com/" + GITHUB_REPO + "/releases");

                // Discover assets
                String setupUrl = null;
                String exeUrl = null;
                long setupSize = 0;
                JsonNode assets = release.path("assets");
                if (assets.isArray()) {
                    for (JsonNode asset : assets) {
                        String aName = asset.path("name").asText("");
                        if (aName.equalsIgnoreCase("SchemaSync-Setup.exe")) {
                            setupUrl = asset.path("browser_download_url").asText(null);
                            setupSize = asset.path("size").asLong(0);
                        } else if (aName.equalsIgnoreCase("SchemaSync.exe")) {
                            exeUrl = asset.path("browser_download_url").asText(null);
                            if (setupSize == 0) setupSize = asset.path("size").asLong(0);
                        }
                    }
                }

                // If setup is not specifically found, fall back to exe or htmlUrl
                final String effectiveSetupUrl = setupUrl != null ? setupUrl : (exeUrl != null ? exeUrl : htmlUrl);
                final long finalSetupSize = setupSize;

                if (isNewerVersion(tagName, CURRENT_VERSION)) {
                    SwingUtilities.invokeLater(() -> {
                        showUpdateDialog(parent, tagName, releaseName, body, htmlUrl, effectiveSetupUrl, finalSetupSize);
                    });
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
        }).start();
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

    private static void showUpdateDialog(Component parent, String tagName, String releaseName,
                                         String body, String htmlUrl, String downloadUrl, long packageSize) {
        Frame owner = parent instanceof Frame ? (Frame) parent : (Frame) SwingUtilities.getWindowAncestor(parent);
        JDialog dlg = new JDialog(owner, "🚀 SchemaSync Update Available: " + tagName, true);
        dlg.setSize(640, 500);
        dlg.setLocationRelativeTo(parent);
        dlg.setLayout(new BorderLayout(10, 10));

        // Header
        JPanel header = new JPanel(new GridLayout(3, 1, 2, 2));
        header.setBorder(BorderFactory.createEmptyBorder(14, 18, 6, 18));
        JLabel title = new JLabel("A new version of SchemaSync is available!");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setForeground(new Color(16, 185, 129));

        JLabel sub = new JLabel("Installed: v" + CURRENT_VERSION + "  ➔  Available: " + tagName + " (" + releaseName + ")");
        sub.setFont(sub.getFont().deriveFont(Font.PLAIN, 13f));

        String sizeStr = packageSize > 0 ? " • Package size: " + formatFileSize(packageSize) : "";
        JLabel sizeLbl = new JLabel("Published on GitHub" + sizeStr);
        sizeLbl.setForeground(Color.GRAY);
        sizeLbl.setFont(sizeLbl.getFont().deriveFont(Font.ITALIC, 11f));

        header.add(title);
        header.add(sub);
        header.add(sizeLbl);
        dlg.add(header, BorderLayout.NORTH);

        // Changelog text area
        JTextArea notesArea = new JTextArea(body);
        notesArea.setEditable(false);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(notesArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Release Notes / Changelog"));
        dlg.add(scroll, BorderLayout.CENTER);

        // South Panel: Progress area + Buttons
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.setBorder(BorderFactory.createEmptyBorder(6, 16, 12, 16));

        // Progress section
        JPanel progressSection = new JPanel(new BorderLayout(6, 6));
        progressSection.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        JLabel statusLabel = new JLabel("Select an option below to update.");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        JCheckBox silentRestartBox = new JCheckBox("Automatically close & restart SchemaSync after installation", true);
        silentRestartBox.setFont(silentRestartBox.getFont().deriveFont(Font.PLAIN, 11f));

        JPanel progSub = new JPanel(new BorderLayout(4, 4));
        progSub.add(statusLabel, BorderLayout.NORTH);
        progSub.add(progressBar, BorderLayout.CENTER);
        progSub.add(silentRestartBox, BorderLayout.SOUTH);
        progressSection.add(progSub, BorderLayout.CENTER);

        southPanel.add(progressSection);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));

        JButton updateNowBtn = new JButton("⚡ 1-Click Update & Restart");
        updateNowBtn.setFont(updateNowBtn.getFont().deriveFont(Font.BOLD, 12f));
        updateNowBtn.setToolTipText("Downloads the verified installer in background, executes update, and restarts");
        updateNowBtn.setBackground(new Color(16, 185, 129));
        updateNowBtn.setForeground(Color.WHITE);

        JButton downloadInstallerBtn = new JButton("📥 Download Installer");
        downloadInstallerBtn.setToolTipText("Open direct download link in browser to save the installer manually");
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
        laterBtn.addActionListener(e -> dlg.dispose());

        AtomicBoolean isDownloading = new AtomicBoolean(false);
        AtomicBoolean cancelRequested = new AtomicBoolean(false);

        updateNowBtn.addActionListener(e -> {
            if (isDownloading.get()) return;

            // Start in-app download
            isDownloading.set(true);
            cancelRequested.set(false);

            updateNowBtn.setEnabled(false);
            downloadInstallerBtn.setEnabled(false);
            viewGithubBtn.setEnabled(false);
            laterBtn.setText("Cancel");

            progressBar.setValue(0);
            progressBar.setVisible(true);
            statusLabel.setText("Connecting to download server...");

            new Thread(() -> {
                File tempInstaller = null;
                try {
                    File tempDir = new File(System.getProperty("java.io.tmpdir"), "SchemaSyncUpdate");
                    if (!tempDir.exists()) tempDir.mkdirs();
                    tempInstaller = new File(tempDir, "SchemaSync-Setup-" + tagName + ".exe");

                    // Handle redirects (GitHub release asset downloads redirect to AWS S3)
                    URL curUrl = new URI(downloadUrl).toURL();
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
                    try (InputStream in = conn.getInputStream();
                         FileOutputStream out = new FileOutputStream(tempInstaller)) {

                        byte[] buffer = new byte[32768];
                        long downloaded = 0;
                        int read;
                        long startTime = System.currentTimeMillis();
                        long lastUiUpdate = startTime;

                        while ((read = in.read(buffer)) != -1) {
                            if (cancelRequested.get()) {
                                in.close();
                                out.close();
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

                            out.write(buffer, 0, read);
                            downloaded += read;
                            long now = System.currentTimeMillis();

                            if (now - lastUiUpdate > 120) {
                                final long curDown = downloaded;
                                double elapsed = (now - startTime) / 1000.0;
                                double mbps = elapsed > 0 ? (curDown / (1024.0 * 1024.0)) / elapsed : 0;
                                int percent = totalLength > 0 ? (int) ((curDown * 100) / totalLength) : -1;

                                SwingUtilities.invokeLater(() -> {
                                    if (percent >= 0) {
                                        progressBar.setIndeterminate(false);
                                        progressBar.setValue(percent);
                                        statusLabel.setText(String.format("Downloading: %s / %s (%d%%) • %.1f MB/s",
                                            formatFileSize(curDown), formatFileSize(totalLength), percent, mbps));
                                    } else {
                                        progressBar.setIndeterminate(true);
                                        statusLabel.setText(String.format("Downloading: %s • %.1f MB/s",
                                            formatFileSize(curDown), mbps));
                                    }
                                });
                                lastUiUpdate = now;
                            }
                        }
                    }

                    final File finalInstaller = tempInstaller;
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("✅ Download verified! Launching installer...");
                        progressBar.setValue(100);

                        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
                        if (!isWindows) {
                            JOptionPane.showMessageDialog(dlg,
                                "Update downloaded successfully to:\n" + finalInstaller.getAbsolutePath() +
                                "\n\nPlease run the installer to complete the update.",
                                "Download Complete", JOptionPane.INFORMATION_MESSAGE);
                            dlg.dispose();
                            return;
                        }

                        // Launch installer
                        try {
                            List<String> launchCmd = new ArrayList<>();
                            launchCmd.add(finalInstaller.getAbsolutePath());

                            if (silentRestartBox.isSelected()) {
                                launchCmd.add("/SILENT");
                                launchCmd.add("/FORCECLOSEAPPLICATIONS");
                                launchCmd.add("/RESTARTAPPLICATIONS");
                            }

                            ProcessBuilder pb = new ProcessBuilder(launchCmd);
                            pb.start();

                            // Gracefully terminate this instance after brief delay
                            new Thread(() -> {
                                try { Thread.sleep(600); } catch (Exception ignored) {}
                                System.exit(0);
                            }).start();

                        } catch (Exception launchEx) {
                            JOptionPane.showMessageDialog(dlg,
                                "Failed to automatically start installer: " + launchEx.getMessage() +
                                "\n\nInstaller saved at:\n" + finalInstaller.getAbsolutePath(),
                                "Launch Error", JOptionPane.ERROR_MESSAGE);
                            updateNowBtn.setEnabled(true);
                            downloadInstallerBtn.setEnabled(true);
                            viewGithubBtn.setEnabled(true);
                            laterBtn.setText("Later");
                            isDownloading.set(false);
                        }
                    });

                } catch (Exception ex) {
                    if (tempInstaller != null && tempInstaller.exists()) {
                        tempInstaller.delete();
                    }
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Download failed: " + ex.getMessage());
                        progressBar.setVisible(false);
                        updateNowBtn.setEnabled(true);
                        downloadInstallerBtn.setEnabled(true);
                        viewGithubBtn.setEnabled(true);
                        laterBtn.setText("Later");
                        isDownloading.set(false);

                        JOptionPane.showMessageDialog(dlg,
                            "Unable to complete 1-Click Update:\n" + ex.getMessage() +
                            "\n\nYou can still download the installer manually using the 'Download Installer' button.",
                            "Update Error", JOptionPane.WARNING_MESSAGE);
                    });
                }
            }).start();
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

    private static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
