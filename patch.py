import sys
import re

with open('src/main/java/com/dbtool/panels/SshTerminalPanel.java', 'r', encoding='utf-8') as f:
    code = f.read()

# Replace fields
code = code.replace('private JTextArea terminalArea = new JTextArea();', 'private com.jediterm.terminal.ui.JediTermWidget terminalArea;')

# Replace constructor init
old_init = """        terminalArea.setEditable(true);
        terminalArea.setBackground(new Color(30, 30, 30));
        terminalArea.setForeground(new Color(212, 212, 212));
        terminalArea.setCaretColor(Color.WHITE);
        terminalArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        terminalArea.setFocusTraversalKeysEnabled(false);"""
new_init = "        terminalArea = new com.jediterm.terminal.ui.JediTermWidget(new com.jediterm.terminal.ui.settings.DefaultSettingsProvider());"
code = code.replace(old_init, new_init)

# Remove key listener
code = re.sub(r'terminalArea\.addKeyListener\(new KeyAdapter\(\) \{.*?\}\);', '', code, flags=re.DOTALL)
# Remove caret listener
code = re.sub(r'terminalArea\.addCaretListener.*?\}\);', '', code, flags=re.DOTALL)

# Fix scroll pane wrapping
code = code.replace('terminalPanel.add(new JScrollPane(terminalArea), BorderLayout.CENTER);', 'terminalPanel.add(terminalArea, BorderLayout.CENTER);')

# Fix connect method
old_connect = """                // Setup Shell
                shellChannel = (ChannelShell) session.openChannel("shell");
                shellChannel.setPtyType("xterm");
                shellChannel.setPtySize(120, 40, 1200, 800);
                InputStream shellIn = shellChannel.getInputStream();
                shellOut = shellChannel.getOutputStream();
                shellChannel.connect(5000);

                if (saveProfileCheck.isSelected()) {
                    String profileName = user + "@" + host + ":" + finalPort;
                    dbManager.saveSshProfile(profileName, host, String.valueOf(finalPort), user, pass);
                }"""

new_connect = """                // Setup Shell
                shellChannel = (ChannelShell) session.openChannel("shell");
                shellChannel.setPtyType("xterm");
                
                if (saveProfileCheck.isSelected()) {
                    String profileName = user + "@" + host + ":" + finalPort;
                    dbManager.saveSshProfile(profileName, host, String.valueOf(finalPort), user, pass);
                }

                com.dbtool.panels.terminal.JSchTtyConnector connector = new com.dbtool.panels.terminal.JSchTtyConnector(shellChannel);
                terminalArea.setTtyConnector(connector);
                terminalArea.start();"""
code = code.replace(old_connect, new_connect)

# Fix terminalArea.setText and append which don't exist
code = code.replace('terminalArea.setText("Connecting to " + user + "@" + host + "...\\n");', '')
code = code.replace('terminalArea.append("\\nConnection failed: " + ex.getMessage());', '')
code = code.replace('terminalArea.append("\\n\\nDisconnected.");', '')
code = code.replace('terminalArea.append("\\nUploaded " + file.getName() + " to " + dirNode.fullPath)', 'System.out.println("Uploaded")')
code = code.replace('terminalArea.append("\\nDownloaded " + fileNode.name + " to " + dest.getAbsolutePath())', 'System.out.println("Downloaded")')
code = code.replace('terminalArea.append("\\nDeleted " + fileNode.name);', 'System.out.println("Deleted");')

# Fix theme button
old_theme = """        JButton themeBtn = new JButton("Light Theme");
        themeBtn.addActionListener(e -> {
            if (themeBtn.getText().equals("Light Theme")) {
                terminalArea.setBackground(Color.WHITE);
                terminalArea.setForeground(Color.BLACK);
                terminalArea.setCaretColor(Color.BLACK);
                themeBtn.setText("Dark Theme");
            } else {
                terminalArea.setBackground(new Color(30, 30, 30));
                terminalArea.setForeground(new Color(212, 212, 212));
                terminalArea.setCaretColor(Color.WHITE);
                themeBtn.setText("Light Theme");
            }
        });"""
new_theme = """        JButton themeBtn = new JButton("Light Theme");
        themeBtn.addActionListener(e -> {
            // Theme toggling for JediTerm can be done via SettingsProvider if needed
        });"""
code = code.replace(old_theme, new_theme)

with open('src/main/java/com/dbtool/panels/SshTerminalPanel.java', 'w', encoding='utf-8') as f:
    f.write(code)
