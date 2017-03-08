/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.pool.jdbc;

import java.sql.Connection;
import org.jooq.impl.DSL;

/**
 *
 */
public class JdbcTest {

    public static void main(String... arg) {
        try {
            int min = 1, max = 1;
            JDBCPool pool = JDBCPool
                    .build("com.mysql.jdbc.Driver",
                            "jdbc:mysql://localhost:3306/pos",
                            "root", "eliu", min, max);
            Connection c = pool.getConnection();
            assert c != null;
            System.out.println(">> " + DSL
                    .using(c)
                    .fetchMany("show tables;"));

        } catch (Exception ex) {
            ex.printStackTrace(System.err);
        }
    }

}
