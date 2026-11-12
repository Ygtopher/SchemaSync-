package com.dbtool.panels;

import com.dbtool.DatabaseManager;
import com.dbtool.core.ai.AiKeyVault;
import com.dbtool.core.ai.LlmMessage;
import com.dbtool.core.ai.LlmProvider;
import com.dbtool.core.ai.LlmProviderRegistry;
import com.dbtool.core.ai.LlmProviderType;
import com.dbtool.core.ai.LlmRequest;
import com.dbtool.core.ai.LlmResponse;
import com.dbtool.core.ai.LlmRole;
import com.dbtool.core.ai.SchemaContextBuilder;
import com.dbtool.core.concurrency.TaskCoordinator;
import com.dbtool.util.ThemeManager;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.text.Element;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Pluggable AI SQL Architect Assistant powered by {@link LlmProvider} SPI (`ServiceLoader`),
 * encrypted OS-Keyring API key storage ({@link AiKeyVault}), Java 21 virtual threads
 * ({@link TaskCoordinator}), MigLayout HiDPI controls, and explicit Schema-Only vs Sample-Rows privacy consent.
 */
public class AiAssistantTab extends JPanel {

    private final DatabaseManager dbManager;
    private final JComboBox<LlmProviderType> providerCombo = new JComboBox<>(LlmProviderType.values());
    private final JComboBox<String> modelCombo = new JComboBox<>();
    private final JPasswordField apiKeyField = new JPasswordField(22);
    private final JTextField endpointField = new JTextField(24);
    private final JCheckBox includeSampleRowsCheck = new JCheckBox("Include Sample Rows (Opt-In — default shares Schema Metadata Only)", false);
    private final JLabel privacyBadge = new JLabel("🔒 Schema-Only Mode");

    private final JTextPane chatHistory = new JTextPane();
    private final HTMLEditorKit htmlKit = new HTMLEditorKit();
    private final HTMLDocument htmlDoc;
    private final JTextArea inputArea = new JTextArea(4, 50);
    private final JButton btnSend = new JButton("Send");
    private final JButton btnSaveKey = new JButton("Save Key");
    private final JButton btnAttach = new JButton("Attach Context...");
    private final JLabel attachLabel = new JLabel();

    private final List<LlmMessage> conversationHistory = new ArrayList<>();
    private String attachedCodeContext = "";

    public AiAssistantTab(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        setLayout(new MigLayout("fill, insets 8, gap 6, hidemode 3", "[grow, fill]", "[][][grow][][]"));

        // 1. Provider / Model / Vault Config Row
        JPanel topPanel = new JPanel(new MigLayout("fillx, insets 4 6, gap 6 4, hidemode 3", "[][grow][][grow][]", "[][]"));
        topPanel.setBorder(BorderFactory.createTitledBorder("LLM Provider Configuration"));

        modelCombo.setEditable(true);
        topPanel.add(new JLabel("Provider:"));
        topPanel.add(providerCombo, "growx");
        topPanel.add(new JLabel("Model:"));
        topPanel.add(modelCombo, "growx");
        JButton btnClear = new JButton("Clear Chat");
        btnClear.addActionListener(e -> clearChat());
        topPanel.add(btnClear, "wrap");

        topPanel.add(new JLabel("API Key (OS Vault):"));
        topPanel.add(apiKeyField, "split 2, growx");
        btnSaveKey.setToolTipText("Encrypt and store API key in OS Keyring via SecretStore");
        btnSaveKey.addActionListener(e -> saveCurrentKey());
        topPanel.add(btnSaveKey);
        topPanel.add(new JLabel("Endpoint:"));
        topPanel.add(endpointField, "span 2, growx");
        add(topPanel, "growx, wrap");

        // 2. Explicit Privacy / Schema Consent Row
        JPanel consentBar = new JPanel(new MigLayout("fillx, insets 2 6, gap 8 2", "[][push][]", "[]"));
        includeSampleRowsCheck.addActionListener(e -> updatePrivacyBadge());
        privacyBadge.setFont(ThemeManager.getUiFont(Font.BOLD, 11));
        updatePrivacyBadge();
        consentBar.add(privacyBadge);
        consentBar.add(includeSampleRowsCheck, "growx");
        add(consentBar, "growx, wrap");

        // 3. Chat History
        chatHistory.setEditable(false);
        htmlDoc = (HTMLDocument) htmlKit.createDefaultDocument();
        chatHistory.setEditorKit(htmlKit);
        chatHistory.setDocument(htmlDoc);
        htmlKit.getStyleSheet().addRule("body { font-family: sans-serif; font-size: 13px; padding: 8px; }");
        htmlKit.getStyleSheet().addRule(".sender { font-weight: bold; margin-bottom: 4px; }");
        htmlKit.getStyleSheet().addRule(".you { color: #3b82f6; }");
        htmlKit.getStyleSheet().addRule(".ai { color: #10b981; }");
        htmlKit.getStyleSheet().addRule(".error { color: #ef4444; }");
        htmlKit.getStyleSheet().addRule(".reasoning { font-style: italic; font-size: 12px; margin-bottom: 6px; padding-left: 8px; border-left: 3px solid #6366f1; }");
        htmlKit.getStyleSheet().addRule(".message { margin-bottom: 12px; }");
        htmlKit.getStyleSheet().addRule("pre { padding: 6px; border: 1px solid #64748b; }");

        add(new JScrollPane(chatHistory), "grow, wrap");

        // 4. Attach Bar
        JPanel attachRow = new JPanel(new MigLayout("insets 0, gap 6", "[][grow]", "[]"));
        btnAttach.addActionListener(e -> attachCode());
        attachRow.add(btnAttach);
        attachRow.add(attachLabel, "growx");
        add(attachRow, "growx, wrap");

        // 5. Prompt Input & Send
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        inputArea.getInputMap().put(KeyStroke.getKeyStroke("ctrl ENTER"), "send");
        inputArea.getActionMap().put("send", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { sendMessage(); }
        });
        btnSend.addActionListener(e -> sendMessage());

        JPanel inputRow = new JPanel(new MigLayout("fillx, insets 0, gap 6", "[grow, fill][]", "[grow]"));
        inputRow.add(new JScrollPane(inputArea), "grow");
        inputRow.add(btnSend, "wmin 96, growy");
        add(inputRow, "growx");

        providerCombo.addActionListener(e -> syncProviderSelection());
        providerCombo.setSelectedItem(LlmProviderType.OPENROUTER);
        syncProviderSelection();
        appendMessage("System", "AI Assistant ready. Select a provider (OpenRouter, OpenAI, Gemini, Anthropic, or local Ollama).", "ai");
    }

    public boolean isIncludeSampleRowsSelected() {
        return includeSampleRowsCheck.isSelected();
    }

    public JComboBox<LlmProviderType> getProviderCombo() {
        return providerCombo;
    }

    private void updatePrivacyBadge() {
        if (includeSampleRowsCheck.isSelected()) {
            privacyBadge.setText("⚠️ Schema + Sample Rows (<=3 rows/table)");
            privacyBadge.setForeground(ThemeManager.getDangerColor());
        } else {
            privacyBadge.setText("🔒 Schema-Only Mode (No Table Data Shared)");
            privacyBadge.setForeground(ThemeManager.getSuccessColor());
        }
    }

    private void syncProviderSelection() {
        LlmProviderType type = (LlmProviderType) providerCombo.getSelectedItem();
        LlmProvider provider = LlmProviderRegistry.getInstance().getProvider(type);
        if (provider == null) return;

        modelCombo.removeAllItems();
        for (String m : provider.getDefaultModels()) {
            modelCombo.addItem(m);
        }
        endpointField.setText(provider.getDefaultEndpoint());
        apiKeyField.setEnabled(provider.requiresApiKey());
        btnSaveKey.setEnabled(provider.requiresApiKey());
        apiKeyField.setText(provider.requiresApiKey() ? AiKeyVault.loadApiKey(type) : "");
    }

    private void saveCurrentKey() {
        LlmProviderType type = (LlmProviderType) providerCombo.getSelectedItem();
        AiKeyVault.saveApiKey(type, new String(apiKeyField.getPassword()));
        appendMessage("System", "Saved encrypted key for " + type.getDisplayName() + " into SecretStore.", "ai");
    }

    private void clearChat() {
        try {
            conversationHistory.clear();
            htmlDoc.remove(0, htmlDoc.getLength());
            appendMessage("System", "Chat history cleared.", "ai");
        } catch (Exception ignored) {}
    }

    private Element appendPlaceholder(String msgId) {
        try {
            String html = "<div class='message'><div class='sender ai'>AI</div><div id='" + msgId + "'>Thinking...</div></div>";
            htmlKit.insertHTML(htmlDoc, htmlDoc.getLength(), html, 0, 0, null);
            chatHistory.setCaretPosition(htmlDoc.getLength());
            return htmlDoc.getElement(msgId);
        } catch (Exception e) {
            return null;
        }
    }

    private void appendMessage(String sender, String htmlBody, String cssClass) {
        try {
            String html = "<div class='message'><div class='sender " + cssClass + "'>" + escapeHtml(sender) + "</div>" + htmlBody + "</div>";
            htmlKit.insertHTML(htmlDoc, htmlDoc.getLength(), html, 0, 0, null);
            chatHistory.setCaretPosition(htmlDoc.getLength());
        } catch (Exception ignored) {}
    }

    private void attachCode() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File[] files = chooser.getSelectedFiles();
        StringBuilder sb = new StringBuilder();
        int fileCount = 0;
        long totalBytes = 0;

        for (File f : files) {
            if (f.isDirectory()) {
                try (Stream<Path> paths = Files.walk(f.toPath())) {
                    for (Path p : (Iterable<Path>) paths::iterator) {
                        if (Files.isRegularFile(p)) {
                            String name = p.toString().toLowerCase();
                            if (name.endsWith(".java") || name.endsWith(".sql") || name.endsWith(".json") || name.endsWith(".xml")) {
                                try {
                                    String content = Files.readString(p);
                                    sb.append("--- File: ").append(p.getFileName()).append(" ---\n").append(content).append("\n\n");
                                    fileCount++;
                                    totalBytes += content.length();
                                    if (totalBytes > 200_000) break;
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                } catch (Exception ignored) {}
            } else {
                try {
                    String content = Files.readString(f.toPath());
                    sb.append("--- File: ").append(f.getName()).append(" ---\n").append(content).append("\n\n");
                    fileCount++;
                } catch (Exception ignored) {}
            }
        }
        attachedCodeContext = sb.toString();
        attachLabel.setText(fileCount > 0 ? "Attached " + fileCount + " file(s) for next prompt" : "");
    }

    private void sendMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;

        LlmProviderType type = (LlmProviderType) providerCombo.getSelectedItem();
        LlmProvider provider = LlmProviderRegistry.getInstance().getProvider(type);
        if (provider == null) return;

        String apiKey = new String(apiKeyField.getPassword()).trim();
        if (provider.requiresApiKey() && apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter or save an API key for " + provider.getDisplayName() + " (or switch to Ollama Local).",
                    "Missing API Key", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Object selModel = modelCombo.getEditor().getItem();
        String model = selModel != null ? selModel.toString().trim() : "";
        String endpoint = endpointField.getText().trim();
        boolean includeSamples = includeSampleRowsCheck.isSelected();

        String finalUserPrompt = attachedCodeContext.isEmpty()
                ? text
                : "Attached Context:\n\n" + attachedCodeContext + "\n\nQuestion:\n" + text;

        appendMessage("You", escapeHtml(text).replace("\n", "<br>"), "you");
        inputArea.setText("");
        attachedCodeContext = "";
        attachLabel.setText("");
        btnSend.setEnabled(false);
        btnSend.setText("Thinking...");

        conversationHistory.add(new LlmMessage(LlmRole.USER, finalUserPrompt));
        List<LlmMessage> historySnapshot = List.copyOf(conversationHistory);
        String msgId = "msg_" + System.currentTimeMillis();
        Element targetEl = appendPlaceholder(msgId);

        TaskCoordinator.shared().submitRunnable("AI Assistant Request (" + provider.getDisplayName() + ")", () -> {
            String schemaCtx = buildSafeSchemaContext(includeSamples);
            String systemPrompt = "You are SchemaSync AI, a principal database engineer. Be concise and accurate.\n\n" + schemaCtx;
            LlmRequest req = new LlmRequest(model, systemPrompt, historySnapshot);
            LlmResponse resp = provider.chat(req, apiKey, endpoint);

            SwingUtilities.invokeLater(() -> {
                btnSend.setEnabled(true);
                btnSend.setText("Send");
                if (resp.success()) {
                    conversationHistory.add(new LlmMessage(LlmRole.ASSISTANT, resp.content()));
                    StringBuilder rendered = new StringBuilder();
                    if (resp.reasoning() != null && !resp.reasoning().isBlank()) {
                        rendered.append("<div class='reasoning'>").append(escapeHtml(resp.reasoning()).replace("\n", "<br>")).append("</div>");
                    }
                    rendered.append(escapeHtml(resp.content()).replace("\n", "<br>"));
                    if (targetEl != null) {
                        try {
                            htmlDoc.setInnerHTML(targetEl, rendered.toString());
                            chatHistory.setCaretPosition(htmlDoc.getLength());
                        } catch (Exception ignored) {}
                    }
                } else {
                    if (targetEl != null) {
                        try {
                            htmlDoc.setInnerHTML(targetEl, "<span class='error'>Error: " + escapeHtml(resp.errorMessage()) + "</span>");
                        } catch (Exception ignored) {}
                    }
                }
            });
        });
    }

    private String buildSafeSchemaContext(boolean includeSamples) {
        if (dbManager == null || !dbManager.isConnected()) {
            return "No database connected.";
        }
        try (Connection conn = dbManager.borrowConnection()) {
            return SchemaContextBuilder.buildContext(conn, includeSamples);
        } catch (Exception e) {
            return "Unable to inspect schema context: " + e.getMessage();
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
