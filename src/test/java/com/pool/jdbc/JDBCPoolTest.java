package com.pool.jdbc;

import com.pool.api.exception.PoolInitializationException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.sql.Connection;
import java.sql.SQLException;

import static com.pool.jdbc.JDBCPool.build;
import static com.pool.jdbc.JDBCUtil.createConnection;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
public class JDBCPoolTest {

    @PrepareForTest({JDBCUtil.class})
    @Test(expected = PoolInitializationException.class)
    public void thresholdsValidationTest() {
        mockStaticClasses();
        build("", "", "", "", 10, 0);
        fail("validation failed!");
    }

    private void mockStaticClasses() {
        try {
            Connection c = mock(Connection.class);
            mockStatic(JDBCUtil.class);
            when(createConnection("",
                    "",
                    "")).thenReturn(c);
            assertNotNull(createConnection("", "", ""));
        } catch (SQLException ex) {
        }
    }

}