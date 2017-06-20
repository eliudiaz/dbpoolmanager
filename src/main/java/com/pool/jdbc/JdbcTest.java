package com.pool.jdbc;

import org.jooq.impl.DSL;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class JdbcTest {

    public static void main(String... arg) {
        try {
            int min = 1, max = 2;
            long exp = 2l, idle = 1l;
            JDBCPool pool = JDBCPool
                    .build("com.mysql.jdbc.Driver",
                            "jdbc:mysql://localhost:3306/pos",
                            "root", "eliu", min, max, exp, idle);
            int cc = 0;
            List<Connection> old = new ArrayList<>();
            while (cc < max) {
                Connection c = pool.getConnection();
                assert c != null;
                old.add(c);
                connectionTest(c);
                cc++;
            }
            Thread.sleep((idle + 1) * 1000);
            retryOldIdle(old);
            retryRecycled(pool);
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
        }
    }

    private static void connectionTest(Connection c) {
        System.out.println(">> " + DSL
                .using(c)
                .fetchMany("show tables;"));
    }

    public static void retryRecycled(JDBCPool pool) {
        try {
            System.out.println(">> recycled free: " + pool.getFreeCount());
            connectionTest(pool.getConnection());
            System.out.println(">> big success for recycled!");
        } catch (Exception e) {
            System.out.println(">> recycled not working!" + e.getMessage());
        }
    }


    public static void retryOldIdle(List<Connection> old) {
        try {
            System.out.println("reusing olds!");
            old.stream().forEach(JdbcTest::connectionTest);
        } catch (Exception e) {
            System.out.println(">> normal, they all were idle connections!" + e.getMessage());
        }
    }

}
