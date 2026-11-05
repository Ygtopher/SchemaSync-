package com.dbtool.panels;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class CommandPaletteDialogTest {

    @Test
    void testFilterPaletteItemsMultiTokenMatching() {
        AtomicBoolean ran = new AtomicBoolean(false);
        List<CommandPaletteDialog.PaletteItem> items = Arrays.asList(
                new CommandPaletteDialog.PaletteItem("TABLE", "ad_cli", "Open table in Browse & Search", "Enter", () -> ran.set(true)),
                new CommandPaletteDialog.PaletteItem("STUDIO", "Index Advisor", "Analyze unindexed FKs and redundant indexes", "", () -> {}),
                new CommandPaletteDialog.PaletteItem("THEME", "Emerald Studio", "Switch to Emerald Dark theme", "", () -> {})
        );

        assertEquals(3, CommandPaletteDialog.filterPaletteItems(items, "").size());
        assertEquals(1, CommandPaletteDialog.filterPaletteItems(items, "ad_cli").size());
        assertEquals(1, CommandPaletteDialog.filterPaletteItems(items, "table browse").size());
        assertEquals(1, CommandPaletteDialog.filterPaletteItems(items, "studio index").size());
        assertEquals(0, CommandPaletteDialog.filterPaletteItems(items, "nonexistent_xyz").size());

        items.get(0).getAction().run();
        assertTrue(ran.get());
    }
}
