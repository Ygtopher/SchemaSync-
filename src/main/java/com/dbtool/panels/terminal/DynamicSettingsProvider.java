package com.dbtool.panels.terminal;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.dbtool.util.ThemeManager;

public class DynamicSettingsProvider extends DefaultSettingsProvider {

    @Override
    public boolean copyOnSelect() {
        return true;
    }

    @Override
    public boolean pasteOnMiddleMouseClick() {
        return true;
    }


    private Boolean isDark = null;

    public void setDark(boolean dark) {
        this.isDark = dark;
    }
    
    public boolean isDark() {
        if (isDark != null) return isDark;
        return ThemeManager.isDarkMode();
    }

    @Override
    public TextStyle getDefaultStyle() {
        if (isDark()) {
            // Use standard black background instead of custom rgb to prevent text background rendering bugs in JediTerm
            return new TextStyle(TerminalColor.WHITE, TerminalColor.BLACK);
        } else {
            return new TextStyle(TerminalColor.BLACK, TerminalColor.WHITE);
        }
    }
}
