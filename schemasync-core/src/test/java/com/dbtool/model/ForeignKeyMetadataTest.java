package com.dbtool.model;

import com.dbtool.core.model.ForeignKeyMetadata;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ForeignKeyMetadataTest {
    @Test
    void testFkMetadata() {
        ForeignKeyMetadata fk = new ForeignKeyMetadata();
        fk.setName("fk_order_user");
        fk.setFkColumn("user_id");
        fk.setPkTable("users");
        fk.setPkColumn("id");
        assertEquals("fk_order_user", fk.getName());
        assertEquals("user_id", fk.getFkColumn());
    }
}
