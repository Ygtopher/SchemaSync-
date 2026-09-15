package com.dbtool.panels.terminal;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.dbtool.util.ThemeManager;

public class DynamicSettingsProvider extends DefaultSettingsProvider {

    // Removed the internal boolean and setter. Now we rely on the global ThemeManager!

    @Override
    public TextStyle getDefaultStyle() {
        if (ThemeManager.isDarkMode()) {
            return new TextStyle(TerminalColor.WHITE, TerminalColor.rgb(30, 30, 30));
        } else {
            return new TextStyle(TerminalColor.BLACK, TerminalColor.WHITE);
        }
    }
}
