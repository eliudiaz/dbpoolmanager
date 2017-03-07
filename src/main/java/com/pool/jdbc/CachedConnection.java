/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.pool.jdbc;

import com.pool.api.PoolBase;
import com.pool.api.PoolItem;
import com.pool.jdbc.exception.ConnectionMaxIddleTimeReachedException;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 *
 */
public class CachedConnection implements Connection, PoolItem {

    private final Connection c;
    private int usages;
    private long lastTransaction;
    private long maxIddleTime; //seconds
    private PoolBase pool;

    public CachedConnection(Connection c, PoolBase pool, long maxIddleTime) {
        this.usages = 0;
        this.c = c;
        this.maxIddleTime = maxIddleTime * 1000L; //comes in seconds
        this.pool = pool;
    }

    private void checkAvailability() {
        final long now = System.currentTimeMillis();
        if ((now - lastTransaction) >= maxIddleTime) { // max iddle time complete
            pool.checkIn(this);
            throw new ConnectionMaxIddleTimeReachedException();
        }
        lastTransaction = now;
    }

    @Override
    public void increaseUsages() {
        usages++;
        lastTransaction = System.currentTimeMillis();
    }

    @Override
    public int usagesCount() {
        return usages;
    }

    @Override
    public Statement createStatement() throws SQLException {
        checkAvailability();
        return c.createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        checkAvailability();
        return c.prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        checkAvailability();
        return c.prepareCall(sql);
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        checkAvailability();
        return c.nativeSQL(sql);
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        checkAvailability();
        c.setAutoCommit(autoCommit);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        checkAvailability();
        return c.getAutoCommit();
    }

    @Override
    public void commit() throws SQLException {
        checkAvailability();
        c.commit();
    }

    @Override
    public void rollback() throws SQLException {
        checkAvailability();
        c.rollback();
    }

    @Override
    public void close() throws SQLException {
        checkAvailability();
        c.close();
    }

    @Override
    public boolean isClosed() throws SQLException {
        checkAvailability();
        return c.isClosed();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkAvailability();
        return c.getMetaData();
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        checkAvailability();
        c.setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        checkAvailability();
        return c.isReadOnly();
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        checkAvailability();
        c.setCatalog(catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        checkAvailability();
        return c.getCatalog();
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        checkAvailability();
        c.setTransactionIsolation(level);
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        checkAvailability();
        return c.getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkAvailability();
        return c.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkAvailability();
        c.clearWarnings();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        checkAvailability();
        return c.createStatement(resultSetType, resultSetConcurrency);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        checkAvailability();
        return c.prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        checkAvailability();
        return c.prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        checkAvailability();
        return c.getTypeMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        checkAvailability();
        c.setTypeMap(map);
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        checkAvailability();
        c.setHoldability(holdability);
    }

    @Override
    public int getHoldability() throws SQLException {
        checkAvailability();
        return c.getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        checkAvailability();
        return c.setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        checkAvailability();
        return c.setSavepoint(name);
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        checkAvailability();
        c.rollback(savepoint);
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        checkAvailability();
        c.releaseSavepoint(savepoint);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        checkAvailability();
        return c.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        checkAvailability();
        return c.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        checkAvailability();
        return c.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        checkAvailability();
        return c.prepareStatement(sql, autoGeneratedKeys);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        checkAvailability();
        return c.prepareStatement(sql, columnIndexes);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        checkAvailability();
        return c.prepareStatement(sql, columnNames);
    }

    @Override
    public Clob createClob() throws SQLException {
        checkAvailability();
        return c.createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        checkAvailability();
        return c.createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        checkAvailability();
        return c.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        checkAvailability();
        return c.createSQLXML();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        checkAvailability();
        return c.isValid(timeout);
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        checkAvailability();
        setClientInfo(name, value);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        checkAvailability();
        setClientInfo(properties);
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        checkAvailability();
        return c.getClientInfo(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        checkAvailability();
        return c.getClientInfo();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        checkAvailability();
        return c.createArrayOf(typeName, elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        checkAvailability();
        return c.createStruct(typeName, attributes);
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        checkAvailability();
        c.setSchema(schema);
    }

    @Override
    public String getSchema() throws SQLException {
        checkAvailability();
        return c.getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        checkAvailability();
        c.abort(executor);
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        checkAvailability();
        c.setNetworkTimeout(executor, milliseconds);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        checkAvailability();
        return c.getNetworkTimeout();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        checkAvailability();
        return c.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        checkAvailability();
        return c.isWrapperFor(iface);
    }

    @Override
    public int hashCode() {
        return c.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CachedConnection other = (CachedConnection) obj;
        if (!Objects.equals(this.c, other.c)) {
            return false;
        }
        return true;
    }

}
