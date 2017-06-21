package com.pool.jdbc;

import com.pool.jdbc.exception.ConnectionMaxIddleTimeReachedException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.sql.Connection;
import java.sql.SQLException;

import static java.lang.Thread.sleep;
import static org.junit.Assert.fail;

@RunWith(MockitoJUnitRunner.class)
public class JDBCConnectionTest {

    @Mock
    private Connection connection;
    @Mock
    private JDBCPool pool;

    @Test(expected = ConnectionMaxIddleTimeReachedException.class)
    public void testAvailability() {
        Long iddleTime = 1L; //1s
        JDBCConnection conn = new JDBCConnection(connection, pool, iddleTime);
        try {
            sleep(iddleTime * 1000 * 2);
            // do any operation
            conn.createStatement(); // should refuse operation
        } catch (InterruptedException | SQLException ex) {
        }
        fail("test failed!");
    }
}
