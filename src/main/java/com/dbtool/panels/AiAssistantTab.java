package com.dbtool.panels;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.dbtool.DatabaseManager;
import java.util.List;

public class AiAssistantTab extends JPanel {
    private JTextPane chatHistory;
    private JTextArea inputArea;
    private JButton btnSend;
    private JPasswordField apiKeyField;
    private JComboBox<String> modelCombo;
    private HTMLEditorKit htmlKit;
    private HTMLDocument htmlDoc;
    private ObjectMapper mapper;
    private ArrayNode conversationHistory;

    private JButton btnAttach;
    private JLabel attachLabel;
    private String attachedCodeContext = "";

    private DatabaseManager dbManager;

    public AiAssistantTab(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        mapper = new ObjectMapper();
        conversationHistory = mapper.createArrayNode();
        
        setLayout(new BorderLayout());
        setBackground(new Color(40, 42, 54));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBackground(new Color(40, 42, 54));
        topPanel.setForeground(Color.WHITE);

        JLabel keyLabel = new JLabel("OpenRouter API Key: ");
        keyLabel.setForeground(Color.WHITE);
        apiKeyField = new JPasswordField(30);
        String envKey = System.getenv("OPENROUTER_API_KEY");
        apiKeyField.setText(envKey != null ? envKey : "");
        
        JLabel modelLabel = new JLabel("  Model: ");
        modelLabel.setForeground(Color.WHITE);
        modelCombo = new JComboBox<>(new String[]{
            "qwen/qwen3.8-27b:free",
            "google/gemma-4-31b-it:free",
            "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free",
            "cohere/north-mini-code:free",
            "liquid/lfm-2.5-2.6b:free"
        });

        JButton btnClear = new JButton("Clear Chat");
        btnClear.addActionListener(e -> clearChat());

        topPanel.add(keyLabel);
        topPanel.add(apiKeyField);
        topPanel.add(modelLabel);
        topPanel.add(modelCombo);
        topPanel.add(btnClear);
        add(topPanel, BorderLayout.NORTH);

        chatHistory = new JTextPane();
        chatHistory.setEditable(false);
        chatHistory.setBackground(new Color(40, 42, 54));
        htmlKit = new HTMLEditorKit();
        htmlDoc = (HTMLDocument) htmlKit.createDefaultDocument();
        chatHistory.setEditorKit(htmlKit);
        chatHistory.setDocument(htmlDoc);
        
        htmlKit.getStyleSheet().addRule("body { font-family: sans-serif; font-size: 14px; color: #f8f8f2; padding: 10px; }");
        htmlKit.getStyleSheet().addRule(".sender { font-weight: bold; margin-bottom: 4px; }");
        htmlKit.getStyleSheet().addRule(".you { color: #8be9fd; }");
        htmlKit.getStyleSheet().addRule(".ai { color: #50fa7b; }");
        htmlKit.getStyleSheet().addRule(".error { color: #ff5555; }");
        htmlKit.getStyleSheet().addRule(".reasoning { color: #bd93f9; font-style: italic; font-size: 12px; margin-bottom: 8px; border-left: 3px solid #6272a4; padding-left: 8px; }");
        htmlKit.getStyleSheet().addRule(".message { margin-bottom: 15px; }");
        htmlKit.getStyleSheet().addRule("pre { background-color: #282a36; border: 1px solid #6272a4; padding: 5px; border-radius: 4px; overflow: auto; }");
        htmlKit.getStyleSheet().addRule("code { font-family: monospace; }");

        JScrollPane scrollHistory = new JScrollPane(chatHistory);
        scrollHistory.setBorder(BorderFactory.createLineBorder(new Color(98, 114, 164)));

        inputArea = new JTextArea(4, 50);
        inputArea.setBackground(new Color(68, 71, 90));
        inputArea.setForeground(Color.WHITE);
        inputArea.setCaretColor(Color.WHITE);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane scrollInput = new JScrollPane(inputArea);

        btnSend = new JButton("Send");
        btnSend.setBackground(new Color(98, 114, 164));
        btnSend.setForeground(Color.WHITE);
        btnSend.setFocusPainted(false);
        btnSend.setFont(new Font("SansSerif", Font.BOLD, 14));

        btnSend.addActionListener(e -> sendMessage());

        inputArea.getInputMap().put(KeyStroke.getKeyStroke("ctrl ENTER"), "send");
        inputArea.getActionMap().put("send", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                sendMessage();
            }
        });

btnAttach = new JButton("Attach Code...");
        btnAttach.setBackground(new Color(68, 71, 90));
        btnAttach.setForeground(Color.WHITE);
        btnAttach.setFocusPainted(false);
        btnAttach.addActionListener(e -> attachCode());

        attachLabel = new JLabel("");
        attachLabel.setForeground(new Color(80, 250, 123));

        JPanel attachPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        attachPanel.setBackground(new Color(40, 42, 54));
        attachPanel.add(btnAttach);
        attachPanel.add(attachLabel);

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBackground(new Color(40, 42, 54));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        bottomPanel.add(attachPanel, BorderLayout.NORTH);
        bottomPanel.add(scrollInput, BorderLayout.CENTER);
        bottomPanel.add(btnSend, BorderLayout.EAST);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollHistory, bottomPanel);
        splitPane.setResizeWeight(0.85);
        splitPane.setDividerSize(5);
        splitPane.setBorder(null);

        add(splitPane, BorderLayout.CENTER);

        initSystemPrompt();
        appendMessage("System", "Welcome to the new OpenRouter AI Assistant! Ready to use <b>" + modelCombo.getSelectedItem() + "</b>.", "ai");
    }

    private void initSystemPrompt() {
        conversationHistory.removeAll();
    }

    
    private String getDatabaseSchemaContext() {
        if (dbManager == null || dbManager.connection == null) return "No database connected.";
        try {
            if (dbManager.connection.isClosed()) return "Database connection is closed.";
            StringBuilder sb = new StringBuilder();
            sb.append("Current Database Schema:\n");
            List<String> tables = dbManager.getTableNames();
            for (String table : tables) {
                sb.append("Table: ").append(table).append("\nColumns: ");
                List<String> cols = dbManager.getColumnNames(table);
                sb.append(String.join(", ", cols)).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error retrieving schema: " + e.getMessage();
        }
    }

    private void clearChat() {
        try {
            htmlDoc.remove(0, htmlDoc.getLength());
            initSystemPrompt();
            appendMessage("System", "Chat history cleared.", "ai");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void appendMessage(String sender, String message, String cssClass) {
        try {
            String html = "<div class='message'><div class='sender " + cssClass + "'>" + sender + "</div>" + message + "</div>";
            htmlKit.insertHTML(htmlDoc, htmlDoc.getLength(), html, 0, 0, null);
            chatHistory.setCaretPosition(htmlDoc.getLength());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
    private void attachCode() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
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
                                if (name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".html") || name.endsWith(".css") || name.endsWith(".sql") || name.endsWith(".xml") || name.endsWith(".json")) {
                                    try {
                                        String content = Files.readString(p);
                                        sb.append("--- File: ").append(p.getFileName().toString()).append(" ---\n");
                                        sb.append(content).append("\n\n");
                                        fileCount++;
                                        totalBytes += content.length();
                                        if (totalBytes > 200000) break; // Limit to ~200KB of text
                                    } catch (Exception ex) {}
                                }
                            }
                        }
                    } catch (Exception ex) {}
                } else {
                    try {
                        String content = Files.readString(f.toPath());
                        sb.append("--- File: ").append(f.getName()).append(" ---\n");
                        sb.append(content).append("\n\n");
                        fileCount++;
                    } catch (Exception ex) {}
                }
            }
            
            attachedCodeContext = sb.toString();
            attachLabel.setText("Attached " + fileCount + " file(s) (will be sent with next message)");
        }
    }

    private void sendMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;

        String apiKey = new String(apiKeyField.getPassword());
        if (apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter your OpenRouter API Key.", "Missing Key", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String model = (String) modelCombo.getSelectedItem();

String finalMessage = text;
        if (!attachedCodeContext.isEmpty()) {
            finalMessage = "Here is some attached code for context:\n\n" + attachedCodeContext + "\n\nUser Query: " + text;
        }

        String displayMsg = text.replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
        if (!attachedCodeContext.isEmpty()) {
            displayMsg += "<br><br><i>[Attached Code Files Included in Request]</i>";
        }
        
        appendMessage("You", displayMsg, "you");
        inputArea.setText("");
        btnSend.setEnabled(false);
        btnSend.setText("Thinking...");
        
        attachedCodeContext = ""; // Clear after sending
        attachLabel.setText("");

        // Add user message to history
        ObjectNode userMsg = mapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", finalMessage);
        conversationHistory.add(userMsg);

        CompletableFuture.runAsync(() -> {
            try {
                String aiResponse = callOpenRouterWithFallback(apiKey, new String[]{model, "google/gemma-4-31b-it:free", "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free", "cohere/north-mini-code:free", "liquid/lfm-2.5-2.6b:free"}, 0);
                SwingUtilities.invokeLater(() -> {
                    if (!aiResponse.startsWith("API Error:")) {
                        // Extract plain content for history (without HTML reasoning blocks)
                        String cleanContent = aiResponse;
                        int noteIdx = cleanContent.indexOf("<i>(Note: Fell back to");
                        if (noteIdx != -1) {
                            cleanContent = cleanContent.substring(cleanContent.indexOf("</i><br><br>") + 12);
                        }
                        // Strip out HTML thinking blocks
                        cleanContent = cleanContent.replaceAll("<div class='reasoning'>.*?</div>", "");
                        cleanContent = cleanContent.replace("&lt;", "<").replace("&gt;", ">").replace("<br>", "\n").replace("<b>", "**").replace("</b>", "**");
                        
                        ObjectNode aiMsg = mapper.createObjectNode();
                        aiMsg.put("role", "assistant");
                        aiMsg.put("content", cleanContent.trim());
                        conversationHistory.add(aiMsg);
                    }
                    appendMessage("AI", aiResponse, aiResponse.startsWith("API Error:") ? "error" : "ai");
                    btnSend.setEnabled(true);
                    btnSend.setText("Send");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    appendMessage("Error", "Failed to connect to AI: " + ex.getMessage(), "error");
                    btnSend.setEnabled(true);
                    btnSend.setText("Send");
                });
            }
        });
    }

    private String callOpenRouterWithFallback(String apiKey, String[] models, int index) throws Exception {
        if (index >= models.length) return "API Error: All fallback models failed or rate-limited.";
        String model = models[index];
        
        String endpoint = "https://openrouter.ai/api/v1/chat/completions";
        
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        ArrayNode finalMessages = mapper.createArrayNode();
        ObjectNode sysMsg = mapper.createObjectNode();
        sysMsg.put("role", "system");
        sysMsg.put("content", "You are a highly advanced Database Architect and Software Engineer. Provide concise, expert answers.\n\n" + getDatabaseSchemaContext());
        finalMessages.add(sysMsg);
        for (JsonNode msg : conversationHistory) {
            finalMessages.add(msg);
        }
        root.set("messages", finalMessages);
        
        String jsonPayload = mapper.writeValueAsString(root);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("HTTP-Referer", "https://github.com/dbtool")
                .header("X-Title", "DBTOOL Assistant")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();

        JsonNode rootNode;
        try {
            rootNode = mapper.readTree(body);
        } catch(Exception e) {
            rootNode = mapper.createObjectNode();
        }

        // Expanded fallback conditions for 429 Rate Limited, 502/503/504 Server Errors, or explicit JSON error object
        if (response.statusCode() == 429 || response.statusCode() == 502 || response.statusCode() == 503 || response.statusCode() == 504 || rootNode.has("error")) {
            return callOpenRouterWithFallback(apiKey, models, index + 1);
        } else if (response.statusCode() != 200) {
            return "API Error: " + response.statusCode() + " - " + body;
        }

        JsonNode choices = rootNode.path("choices");
        
        if (choices.isArray() && choices.size() > 0) {
            JsonNode messageNode = choices.get(0).path("message");
            String parsedResponse = "";
            
            if (messageNode.has("reasoning") && !messageNode.get("reasoning").isNull()) {
                String reasoning = messageNode.get("reasoning").asText().replace("\n", "<br>");
                parsedResponse += "<div class='reasoning'>[Thinking...]<br>" + reasoning + "</div>";
            } else if (messageNode.has("reasoning_content") && !messageNode.get("reasoning_content").isNull()) {
                String reasoning = messageNode.get("reasoning_content").asText().replace("\n", "<br>");
                parsedResponse += "<div class='reasoning'>[Thinking...]<br>" + reasoning + "</div>";
            }

            if (messageNode.has("content") && !messageNode.get("content").isNull()) {
                String content = messageNode.get("content").asText();
                
                int thinkEnd = content.indexOf("</think>");
                if (content.startsWith("<think>") && thinkEnd != -1) {
                    String reasoning = content.substring(7, thinkEnd).trim().replace("\n", "<br>");
                    parsedResponse += "<div class='reasoning'>[Thinking...]<br>" + reasoning + "</div>";
                    content = content.substring(thinkEnd + 8).trim();
                }
                
                content = content.replace("<", "&lt;").replace(">", "&gt;");
                content = content.replace("\n", "<br>");
                content = content.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
                
                parsedResponse += (index > 0 ? "<i>(Note: Fell back to " + model + " due to rate limits)</i><br><br>" : "") + content;
                return parsedResponse;
            }
        }
        
        return "Could not parse AI response. Raw: " + body;
    }
}
