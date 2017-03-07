/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.pool.jdbc;

import com.pool.api.PoolInitializer;
import com.pool.api.PoolBase;
import com.pool.api.exception.PoolInitializationException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
public class JDBConnectionPool extends PoolBase<CachedConnection> {

    private final String dsn;
    private final String usr;
    private final String pwd;
    private final Long maxIddleTime;

    public static JDBConnectionPool build(String dsn, String usr, String pwd) {
        return new JDBConnectionPool(dsn, usr, pwd);
    }

    public static JDBConnectionPool build(String dsn, String usr, String pwd,
            Integer minConnections, Integer maxConnections) {
        return new JDBConnectionPool(usr, dsn, usr, pwd,
                minConnections, maxConnections, 30L, 10L);
    }

    public JDBConnectionPool(String dsn, String usr, String pwd) {
        this(usr, dsn, usr, pwd, 2, 10, 30L, 10L);
    }

    public JDBConnectionPool(String dsn, String usr, String pwd,
            Integer minConnections, Integer maxConnections) {
        this(usr, dsn, usr, pwd, minConnections, maxConnections, 30L, 10L);
    }

    public JDBConnectionPool(
            String driver,
            String dsn,
            String usr,
            String pwd,
            Integer maxPoolSize,
            Integer minPoolSize,
            Long expirationTime,
            Long maxIddleTime) {
        super(maxPoolSize, minPoolSize, expirationTime);
        try {
            Class.forName(driver).newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace(System.err);
            throw new PoolInitializationException(e.getMessage(), e);
        }
        this.dsn = dsn;
        this.usr = usr;
        this.pwd = pwd;
        this.maxIddleTime = maxIddleTime;
        if (minPoolSize > 0) {
            PoolBase.initer
                    = new PoolInitializer<CachedConnection>(JDBConnectionPool.this,
                            minPoolSize);
            PoolBase.initer.start();
        }
    }

    private SQLException throwErrorGettingConnectionException() {
        log.warn("Error getting connection");
        return new SQLException("Error getting connection!");
    }

    public Connection getConnection() throws SQLException {
        try {
            return Optional
                    .ofNullable(super.checkOut())
                    .orElseThrow(this::throwErrorGettingConnectionException);

        } catch (Exception ex) {
            log.error("Error getting connection", ex);
            if (!(ex instanceof SQLException)) {
                Throwable t = ex.getCause();
                while (t != null) {
                    log.warn("Error getting connection", ex);
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
            return new CachedConnection(DriverManager
                    .getConnection(dsn, usr, pwd), this, maxIddleTime);
        } catch (SQLException e) {
            log.debug("error creating!!", e);
            return (null);
        }
    }

    @Override
    public void expire(CachedConnection o) {
        try {
            o.close();
        } catch (SQLException e) {
            log.debug("error expiring!!", e);
        }
    }

    @Override
    public boolean validate(CachedConnection o) {
        try {
            return (!o.isClosed());
        } catch (SQLException e) {
            log.debug("error validating!!", e);
            return (false);
        }
    }
}
