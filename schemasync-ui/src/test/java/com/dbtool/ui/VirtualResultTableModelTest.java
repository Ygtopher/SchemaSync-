package com.dbtool.ui;

import com.dbtool.core.query.PagedResultWindow;
import com.dbtool.core.query.RowData;
import org.junit.jupiter.api.Test;

import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VirtualResultTableModelTest {

    @Test
    void testDefaultTableModelCompatibilityAndCellEditWithinSlidingWindow() {
        PagedResultWindow window = new PagedResultWindow(
                Arrays.asList("id", "status"),
                10_000,
                250,
                1000,
                (offset, limit) -> {
                    List<RowData> page = new ArrayList<>(limit);
                    for (int i = 0; i < limit; i++) {
                        int id = offset + i;
                        page.add(new RowData(new Object[]{id, "NEW"}));
                    }
                    return page;
                }
        );

        VirtualResultTableModel model = new VirtualResultTableModel(window, new String[]{"INT", "VARCHAR"});
        assertTrue(model instanceof DefaultTableModel);
        assertEquals(10_000, model.getRowCount());
        assertEquals(2, model.getColumnCount());
        assertEquals("INT", model.getColumnType(0));

        assertEquals(5005, model.getValueAt(5005, 0));
        assertEquals("NEW", model.getValueAt(5005, 1));

        model.setValueAt("DONE", 5005, 1);
        assertEquals("DONE", model.getValueAt(5005, 1));
        assertTrue(model.getCachedRowCount() <= 1000);
    }
}
