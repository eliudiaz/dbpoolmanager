package com.pool.jdbc;

import com.pool.jdbc.exception.ConnectionMaxIddleTimeReachedException;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class JDBCConnectionTest {

    @Mock
    Connection connection;
    @Mock
    JDBCPool pool;

    @Test(expected = ConnectionMaxIddleTimeReachedException.class)
    public void testAvailability() {
        Long iddleTime = 1L; //1s
        JDBCConnection conn = new JDBCConnection(connection, pool, iddleTime);
        try {
            Thread.sleep(iddleTime * 1000 * 2);
            // do any operation 
            conn.createStatement(); // should refuse operation
        } catch (InterruptedException | SQLException ex) {
        }
        Assert.fail("test failed!");
    }
}
