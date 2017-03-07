/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.pool.jdbc;

import com.pool.jdbc.exception.ConnectionMaxIddleTimeReachedException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 *
 * @author edcracken
 */
@RunWith(MockitoJUnitRunner.class)
public class JDBCConnectionTest {

    @Mock
    Connection connection;
    @Mock
    JDBPool pool;

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
