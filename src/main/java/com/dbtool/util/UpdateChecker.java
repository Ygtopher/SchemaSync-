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

                // Discover assets across platforms (Windows .exe, Ubuntu .deb, Fedora .rpm, Universal .jar)
                String setupUrl = null;
                String exeUrl = null;
                String debUrl = null;
                String debName = null;
                long debSize = 0;
                String rpmUrl = null;
                String rpmName = null;
                long rpmSize = 0;
                String jarUrl = null;
                String jarName = null;
                long jarSize = 0;
                long setupSize = 0;

                JsonNode assets = release.path("assets");
                if (assets.isArray()) {
                    for (JsonNode asset : assets) {
                        String aName = asset.path("name").asText("");
                        String aUrl = asset.path("browser_download_url").asText(null);
                        long aSize = asset.path("size").asLong(0);
                        if (aName.equalsIgnoreCase("SchemaSync-Setup.exe")) {
                            setupUrl = aUrl;
                            setupSize = aSize;
                        } else if (aName.equalsIgnoreCase("SchemaSync.exe")) {
                            exeUrl = aUrl;
                            if (setupSize == 0) setupSize = aSize;
                        } else if (aName.toLowerCase().endsWith(".deb")) {
                            debUrl = aUrl;
                            debName = aName;
                            debSize = aSize;
                        } else if (aName.toLowerCase().endsWith(".rpm")) {
                            rpmUrl = aUrl;
                            rpmName = aName;
                            rpmSize = aSize;
                        } else if (aName.toLowerCase().endsWith(".jar")) {
                            jarUrl = aUrl;
                            jarName = aName;
                            jarSize = aSize;
                        }
                    }
                }

                boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
                boolean hasDpkg = !isWindows && new File("/usr/bin/dpkg").exists();
                boolean hasRpm = !isWindows && !hasDpkg && new File("/usr/bin/rpm").exists();

                File runningJar = getRunningJarFile();
                boolean isRunningExe = isRunningFromExe();

                // 1. Manual browser download package ("📥 Download Package")
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
                        manualDownloadSize = setupSize;
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
                } else if (debUrl != null) {
                    manualDownloadUrl = debUrl;
                    manualDownloadFileName = debName != null ? debName : "schemasync_update.deb";
                    manualDownloadSize = debSize;
                } else {
                    manualDownloadUrl = setupUrl != null ? setupUrl : (exeUrl != null ? exeUrl : htmlUrl);
                    manualDownloadFileName = "SchemaSync-Update";
                    manualDownloadSize = setupSize;
                }

                // 2. Automated 1-Click Update & Restart package ("⚡ 1-Click Update & Restart")
                String autoUpdateUrl;
                String autoUpdateFileName;

                if (isWindows) {
                    if (runningJar != null && !isRunningExe && jarUrl != null) {
                        // Running via java -jar SchemaSync.jar: update running JAR directly
                        autoUpdateUrl = jarUrl;
                        autoUpdateFileName = "SchemaSync.jar";
                    } else if (setupUrl != null) {
                        // Running installed or native executable: use Inno Setup installer
                        autoUpdateUrl = setupUrl;
                        autoUpdateFileName = "SchemaSync-Setup-" + tagName + ".exe";
                    } else if (exeUrl != null) {
                        // Standalone portable SchemaSync.exe
                        autoUpdateUrl = exeUrl;
                        autoUpdateFileName = "SchemaSync-" + tagName + ".exe";
                    } else if (jarUrl != null) {
                        autoUpdateUrl = jarUrl;
                        autoUpdateFileName = "SchemaSync.jar";
                    } else {
                        autoUpdateUrl = manualDownloadUrl;
                        autoUpdateFileName = manualDownloadFileName;
                    }
                } else {
                    // Linux: prefer universal JAR for password-free user-level update
                    if (jarUrl != null) {
                        autoUpdateUrl = jarUrl;
                        autoUpdateFileName = "SchemaSync.jar";
                    } else if (hasDpkg && debUrl != null) {
                        autoUpdateUrl = debUrl;
                        autoUpdateFileName = debName != null ? debName : "schemasync_update.deb";
                    } else if (hasRpm && rpmUrl != null) {
                        autoUpdateUrl = rpmUrl;
                        autoUpdateFileName = rpmName != null ? rpmName : "schemasync_update.rpm";
                    } else {
                        autoUpdateUrl = manualDownloadUrl;
                        autoUpdateFileName = manualDownloadFileName;
                    }
                }

                final String finalManualUrl = manualDownloadUrl;
                final String finalManualName = manualDownloadFileName;
                final long finalManualSize = manualDownloadSize;
                final String finalAutoUrl = autoUpdateUrl;
                final String finalAutoName = autoUpdateFileName;

                if (isNewerVersion(tagName, CURRENT_VERSION)) {
                    SwingUtilities.invokeLater(() -> {
                        showUpdateDialog(parent, tagName, releaseName, body, htmlUrl,
                                finalManualUrl, finalManualName, finalManualSize,
                                finalAutoUrl, finalAutoName);
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
                                         String body, String htmlUrl, String downloadUrl,
                                         String downloadFileName, long packageSize,
                                         String autoUpdateUrl, String autoUpdateFileName) {
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

        String sizeStr = packageSize > 0 ? " • Package: " + downloadFileName + " (" + formatFileSize(packageSize) + ")" : "";
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
        updateNowBtn.setToolTipText("Downloads the verified package in background, installs update, and restarts SchemaSync");
        updateNowBtn.setBackground(new Color(16, 185, 129));
        updateNowBtn.setForeground(Color.WHITE);

        JButton downloadInstallerBtn = new JButton("📥 Download Package");
        downloadInstallerBtn.setToolTipText("Open direct download link in browser to save the package manually");
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
                    tempInstaller = new File(tempDir, autoUpdateFileName);

                    // Handle redirects (GitHub release asset downloads redirect to AWS S3)
                    URL curUrl = new URI(autoUpdateUrl).toURL();
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
                    boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

                    if (isWindows) {
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setIndeterminate(true);
                            statusLabel.setText("📦 Applying update and restarting SchemaSync...");
                        });
                        installAndRestartOnWindows(finalInstaller, tagName, silentRestartBox.isSelected(), dlg);
                    } else {
                        // Ubuntu / Linux 1-Click Update & Restart
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setIndeterminate(true);
                            statusLabel.setText("📦 Applying update and restarting SchemaSync...");
                        });
                        installAndRestartOnLinux(finalInstaller, tagName, silentRestartBox.isSelected(), dlg);
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
                            "\n\nYou can still download the package manually using the 'Download Package' button.",
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

            java.nio.file.Files.write(updateBatch.toPath(), batchContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", updateBatch.getAbsolutePath()).start();
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

            java.nio.file.Files.write(updateBatch.toPath(), batchContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", updateBatch.getAbsolutePath()).start();
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

            java.nio.file.Files.write(updateBatch.toPath(), batchContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", updateBatch.getAbsolutePath()).start();
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
            // 1. Always extract into ~/.local/share/schemasync/SchemaSync.jar (password-free user-level install)
            updatedUserJar = extractJarFromDeb(downloadedFile, userJar);
            if (updatedUserJar) {
                installUserLauncherAndDesktopEntry(userHome, userLauncher, userJar);
            }

            // 2. If running from another user-writable local JAR (e.g. project release/SchemaSync.jar), update it too
            if (runningJar != null && !runningJar.equals(systemJar) && !runningJar.equals(userJar) && runningJar.canWrite()) {
                updatedLocalJar = extractJarFromDeb(downloadedFile, runningJar);
            }

            // 3. Optionally update system package if /usr/share/schemasync exists and pkexec is available (non-fatal)
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
            java.nio.file.Files.copy(downloadedFile.toPath(), userJar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            updatedUserJar = true;
            installUserLauncherAndDesktopEntry(userHome, userLauncher, userJar);
            if (runningJar != null && !runningJar.equals(userJar) && runningJar.canWrite()) {
                java.nio.file.Files.copy(downloadedFile.toPath(), runningJar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                updatedLocalJar = true;
            }
        } else if (fileName.endsWith(".rpm")) {
            // If pkexec is available, optionally install RPM
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

        // Restart SchemaSync detached
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
            java.nio.file.Files.write(userLauncher.toPath(), script.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
            java.nio.file.Files.write(desktopFile.toPath(), desktop.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Extract high-DPI and standard icons for desktop/dock
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
                java.nio.file.Files.copy(in, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
        try (java.io.FileInputStream fis = new java.io.FileInputStream(debFile);
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
                                java.nio.file.Files.move(
                                        tempOut.toPath(),
                                        targetJar.toPath(),
                                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
