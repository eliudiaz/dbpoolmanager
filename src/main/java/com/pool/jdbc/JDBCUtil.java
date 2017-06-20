package com.pool.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 *
 */
public class JDBCUtil {

    public static void validateDriverClass(String driver) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        try {
            Class.forName(driver).newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace(System.err);
            throw e;
        }
    }

    public static Connection createConnection(String dsn, String usr, String pwd) throws SQLException {
        return DriverManager
                .getConnection(dsn, usr, pwd);
    }

}
