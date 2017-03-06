/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.pool.db;

import com.pool.db.exception.PoolInitializationException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

/**
 *
 * @author eliud
 */
public class DBConnectionPool extends PoolBase<CachedConnection> {

    private final String dsn;
    private final String usr;
    private final String pwd;

    public DBConnectionPool(
            String driver, String dsn, String usr, String pwd,
            Integer maxPoolSize, Integer minPoolSize) {
        super(maxPoolSize, minPoolSize);
        try {
            Class.forName(driver).newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace(System.err);
            throw new PoolInitializationException(e.getMessage(), e);
        }
        this.dsn = dsn;
        this.usr = usr;
        this.pwd = pwd;
        if (minPoolSize > 0) {
            PoolBase.initer = new InitializerProcess<CachedConnection>(DBConnectionPool.this, minPoolSize);
        }
    }

    private SQLException throwErrorGettingConnectionException() {
        //                    log_warn("Error getting connection", ex);
        return new SQLException("Error getting connection!");
    }

    public Connection getConnection() throws SQLException {
        try {
            return Optional
                    .ofNullable(super.checkOut())
                    .orElseThrow(this::throwErrorGettingConnectionException);

        } catch (Exception ex) {
//            log_warn("Error getting connection", ex);
            if (!(ex instanceof SQLException)) {
                Throwable t = ex.getCause();
                while (t != null) {
//                    log_warn("Error getting connection", ex);
                    t = t.getCause();
                }
                throw new SQLException(ex.getMessage());
            }
            throw ex;
        }
    }

    @Override
    protected CachedConnection create() {
        try {
            return new CachedConnection(DriverManager.getConnection(dsn, usr, pwd));
        } catch (SQLException e) {
            e.printStackTrace();
            return (null);
        }
    }

    @Override
    public void expire(CachedConnection o) {
        try {
            o.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean validate(CachedConnection o) {
        try {
            return (!o.isClosed());
        } catch (SQLException e) {
            e.printStackTrace();
            return (false);
        }
    }
}
