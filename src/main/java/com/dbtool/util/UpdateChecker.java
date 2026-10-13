package com.dbtool.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class UpdateChecker {

    public static final String CURRENT_VERSION = com.dbtool.Main.APP_VERSION;
    public static final String GITHUB_REPO = "Ygtopher/SchemaSync-";
    private static final String API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

    public static void checkForUpdates(Component parent, boolean silentIfUpToDate) {
        new Thread(() -> {
            try {
                URL url = new URI(API_URL).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "SchemaSync/" + CURRENT_VERSION);
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 404) {
                    // No release published yet on GitHub repo
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

                // Find direct download link for installer if available
                String downloadUrl = htmlUrl;
                JsonNode assets = release.path("assets");
                if (assets.isArray()) {
                    for (JsonNode asset : assets) {
                        String aName = asset.path("name").asText("");
                        if (aName.endsWith(".exe")) {
                            downloadUrl = asset.path("browser_download_url").asText(downloadUrl);
                            break;
                        }
                    }
                }

                String cleanRemote = tagName.replaceAll("[^0-9.]", "");
                String cleanLocal = CURRENT_VERSION.replaceAll("[^0-9.]", "");

                if (isNewerVersion(cleanRemote, cleanLocal)) {
                    final String finalDownloadUrl = downloadUrl;
                    SwingUtilities.invokeLater(() -> {
                        showUpdateDialog(parent, tagName, releaseName, body, htmlUrl, finalDownloadUrl);
                    });
                } else {
                    if (!silentIfUpToDate) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(parent,
                                "✅ You're up to date!\n\nSchemaSync v" + CURRENT_VERSION + " is the latest version available.",
                                "SchemaSync Up to Date", JOptionPane.INFORMATION_MESSAGE);
                        });
                    }
                }

            } catch (Exception ex) {
                if (!silentIfUpToDate) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(parent,
                            "Could not check for updates:\n" + ex.getMessage(),
                            "Update Check", JOptionPane.WARNING_MESSAGE);
                    });
                }
            }
        }).start();
    }

    private static boolean isNewerVersion(String remote, String current) {
        if (remote == null || remote.isEmpty()) return false;
        String[] rParts = remote.split("\\.");
        String[] cParts = current.split("\\.");
        int len = Math.max(rParts.length, cParts.length);
        for (int i = 0; i < len; i++) {
            int r = i < rParts.length ? Integer.parseInt(rParts[i]) : 0;
            int c = i < cParts.length ? Integer.parseInt(cParts[i]) : 0;
            if (r > c) return true;
            if (r < c) return false;
        }
        return false;
    }

    private static void showUpdateDialog(Component parent, String tagName, String releaseName,
                                         String body, String htmlUrl, String downloadUrl) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(parent), "🎉 Update Available: " + tagName, true);
        dlg.setSize(600, 420);
        dlg.setLocationRelativeTo(parent);
        dlg.setLayout(new BorderLayout(10, 10));

        JPanel header = new JPanel(new GridLayout(2, 1, 4, 4));
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 6, 16));
        JLabel title = new JLabel("A new version of SchemaSync is available!");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setForeground(new Color(16, 185, 129));
        JLabel sub = new JLabel("Installed: v" + CURRENT_VERSION + "  ➔  Latest: " + tagName + " (" + releaseName + ")");
        header.add(title);
        header.add(sub);
        dlg.add(header, BorderLayout.NORTH);

        JTextArea notesArea = new JTextArea(body);
        notesArea.setEditable(false);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(notesArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Release Notes"));
        dlg.add(scroll, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));

        JButton downloadBtn = new JButton("📥 Download Installer");
        downloadBtn.setFont(downloadBtn.getFont().deriveFont(Font.BOLD));
        downloadBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(downloadUrl));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, "Error opening download link: " + ex.getMessage());
            }
        });
        btnPanel.add(downloadBtn);

        JButton viewGithubBtn = new JButton("🌐 View on GitHub");
        viewGithubBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(htmlUrl));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, "Error opening GitHub link: " + ex.getMessage());
            }
        });
        btnPanel.add(viewGithubBtn);

        JButton closeBtn = new JButton("Later");
        closeBtn.addActionListener(e -> dlg.dispose());
        btnPanel.add(closeBtn);

        dlg.add(btnPanel, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }
}
