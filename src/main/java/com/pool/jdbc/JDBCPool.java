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
import java.sql.SQLException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
public class JDBCPool extends PoolBase<JDBCConnection> {

    private final String dsn;
    private final String usr;
    private final String pwd;
    private final Long maxIdleTime;

    public static JDBCPool build(String driver, String dsn, String usr, String pwd) {
        return new JDBCPool(driver, dsn, usr, pwd);
    }

    public static JDBCPool build(String driver, String dsn, String usr, String pwd,
            Integer minConnections, Integer maxConnections) {
        return new JDBCPool(driver, dsn, usr, pwd,
                maxConnections, minConnections, 30L, 10L);
    }

    public static JDBCPool build(String driver, String dsn, String usr, String pwd,
            Integer minConnections, Integer maxConnections,
            Long expirationTime, Long idleTime) {
        return new JDBCPool(driver, dsn, usr, pwd,
                maxConnections, minConnections, expirationTime, idleTime);
    }

    public JDBCPool(String driver, String dsn, String usr, String pwd) {
        this(driver, dsn, usr, pwd, 2, 10, 30L, 10L);
    }

    public JDBCPool(String driver, String dsn, String usr, String pwd,
            Integer minConnections, Integer maxConnections) {
        this(driver, dsn, usr, pwd, maxConnections, minConnections, 30L, 10L);
    }

    public JDBCPool(
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
            JDBCUtil.validateDriverClass(driver);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace(System.err);
            throw new PoolInitializationException(e.getMessage(), e);
        }
        this.dsn = dsn;
        this.usr = usr;
        this.pwd = pwd;
        this.maxIdleTime = maxIddleTime;        
        if (minPoolSize > 0) {
            PoolBase.initer
                    = new PoolInitializer<JDBCConnection>(JDBCPool.this,
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
                throw new SQLException(ex.getMessage(), ex);
            }
            throw ex;
        }
    }

    @Override
    protected JDBCConnection create() {
        try {
            return new JDBCConnection(
                    JDBCUtil.createConnection(dsn, usr, pwd),
                    this, maxIdleTime);
        } catch (SQLException e) {
            log.debug("error creating!!", e);
            return (null);
        }
    }

    @Override
    public void expire(JDBCConnection o) {
        try {
            o.close();
        } catch (SQLException e) {
            log.debug("error expiring!!", e);
        }
    }

    @Override
    public boolean validate(JDBCConnection o) {
        try {
            return (!o.isClosed());
        } catch (SQLException e) {
            log.debug("error validating!!", e);
            return (false);
        }
    }

    @Override
    public String toString() {
        return super.toString();
    }    
    
}
