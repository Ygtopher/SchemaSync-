package com.dbtool.panels;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import static org.junit.jupiter.api.Assertions.*;

public class RowInspectorDrawerTest {

    @Test
    void testInspectRowAndEmptyState() {
        RowInspectorDrawer drawer = new RowInspectorDrawer(null, null, null);
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{
                        {203000148, "GITWA GABIRO", null}
                },
                new Object[]{"id_client", "adresse", "email"}
        );
        JTable table = new JTable(model);

        assertDoesNotThrow(() -> drawer.inspectRow("ad_cli", table, 0));
        assertDoesNotThrow(drawer::showEmptyState);
    }
}
