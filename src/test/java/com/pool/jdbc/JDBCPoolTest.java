/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.pool.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 *
 */
@RunWith(PowerMockRunner.class)
public class JDBCPoolTest {

    @PrepareForTest({JDBCUtil.class})
    @Test
    public void maxConnectionsValidation() {

        mockStaticClasses();
        JDBCPool pool = JDBCPool.build("", "", "", "", 0, 2);
        while (true) {
            try {
                pool.getConnection();
            } catch (SQLException ex) {
            }
        }
    }

    private void mockStaticClasses() {
        Connection c = PowerMockito.mock(Connection.class);
        PowerMockito.mockStatic(JDBCUtil.class);
        PowerMockito.when(JDBCUtil.createConnection("",
                "",
                "")).thenReturn(c);
        Assert.assertNotNull(JDBCUtil.createConnection("", "", ""));
    }

}
