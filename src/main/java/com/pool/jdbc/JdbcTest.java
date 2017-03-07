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
            int min = 2, max = 10;
            JDBConnectionPool pool = new JDBConnectionPool("com.mysql.jdbc.Driver",
                    "jdbc:mysql://localhost:3306/pos",
                    "root", "eliu", min, max);
            Connection c = pool.getConnection();
            assert c != null;
            System.out.println(">> " + DSL
                    .using(c)
                    .fetchMany("select count(*) from products"));

        } catch (Exception ex) {
            ex.printStackTrace(System.err);
        }
    }

}
