/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.pool.jdbc;

import com.pool.api.exception.MaxPoolSizeReachedException;
import java.sql.Connection;
import java.sql.SQLException;
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
        int c = 1;
        JDBCPool pool = JDBCPool.build("", "", "", "", 0, c);
        while (c > 0) {
            try {
                pool.getConnection();
            } catch (SQLException ex) {
                Assert.assertTrue(ex.getCause() instanceof MaxPoolSizeReachedException);
            }
            c--;
        }
    }

    private void mockStaticClasses() {
        try {
            Connection c = PowerMockito.mock(Connection.class);
            PowerMockito.mockStatic(JDBCUtil.class);
            PowerMockito.when(JDBCUtil.createConnection("",
                    "",
                    "")).thenReturn(c);
            Assert.assertNotNull(JDBCUtil.createConnection("", "", ""));
        } catch (SQLException ex) {
        }
    }

}
