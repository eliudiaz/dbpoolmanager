package com.pool.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import static java.lang.Class.forName;
import static java.sql.DriverManager.getConnection;

public class JDBCUtil {

    public static void validateDriverClass(String driver) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        try {
            forName(driver).newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace(System.err);
            throw e;
        }
    }

    public static Connection createConnection(String dsn, String usr, String pwd) throws SQLException {
        return getConnection(dsn, usr, pwd);
    }

}
