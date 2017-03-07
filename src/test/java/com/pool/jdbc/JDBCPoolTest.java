/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.pool.jdbc;

import java.sql.Connection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 *
 * @author edcracken
 */
@RunWith(PowerMockRunner.class)
public class JDBCPoolTest {

    @PrepareForTest({SQLUtil.class})
    @Test
    public void mockStaticClassTest() {
        Connection c = PowerMockito.mock(Connection.class);
        PowerMockito.mockStatic(SQLUtil.class);
        PowerMockito.when(SQLUtil.createConnection("",
                "",
                "")).thenReturn(c);
        Assert.assertNotNull(SQLUtil.createConnection("", "", ""));
    }

}
