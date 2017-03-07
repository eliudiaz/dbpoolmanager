/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.pool.jdbc;

import org.jooq.impl.DSL;

/**
 *
 */
public class JdbcTest {

    public static void main(String... arg) {
        try {
            JDBConnectionPool pool = new JDBConnectionPool("com.mysql.jdbc.Driver",
                    "jdbc:mysql://localhost:3306/pos",
                    "root", "eliu", 2, 10);
            System.out.println(">> " + DSL
                    .using(pool.getConnection())
                    .fetchMany("select count(*) from products"));

        } catch (Exception ex) {
            ex.printStackTrace(System.err);
        }
    }

}
