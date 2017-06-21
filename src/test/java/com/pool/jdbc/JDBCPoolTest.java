package com.pool.jdbc;

import com.pool.api.exception.PoolInitializationException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Created by eliud on 6/21/2017.
 */
@RunWith(PowerMockRunner.class)
public class JDBCPoolTest {

    @PrepareForTest({JDBCUtil.class})
    @Test(expected = PoolInitializationException.class)
    public void thresholdsValidationTest() {
        mockStaticClasses();
        JDBCPool.build("", "", "", "", 10, 0);
        Assert.fail("validation failed!");
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