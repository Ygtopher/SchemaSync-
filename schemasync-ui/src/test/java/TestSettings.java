import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TerminalColor;

public class TestSettings extends DefaultSettingsProvider {
    @Override
    public TextStyle getDefaultStyle() {
        return new TextStyle(TerminalColor.WHITE, TerminalColor.BLACK);
    }
}
