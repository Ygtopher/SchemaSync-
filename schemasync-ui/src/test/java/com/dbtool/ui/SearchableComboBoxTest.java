package com.dbtool.ui;

import com.dbtool.ui.components.SearchableComboBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SearchableComboBoxTest {
    @Test
    void testCombo() {
        SearchableComboBox<String> cb = new SearchableComboBox<>();
        assertTrue(cb.isEditable());
    }
}
