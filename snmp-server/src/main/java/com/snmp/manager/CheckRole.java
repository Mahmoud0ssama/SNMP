package com.snmp.manager;
import java.sql.*;
public class CheckRole {
    public static void main(String[] args) throws Exception {
        String dbUrl = "jdbc:postgresql://ep-divine-shape-a2n4rsv4.eu-central-1.aws.neon.tech/snmp_db?sslmode=require";
        String user = "snmp_db_owner";
        String pass = "aD0Qc5OWhKxs";
        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT username, role FROM users")) {
            while (rs.next()) {
                System.out.println("USER: " + rs.getString("username") + ", ROLE: " + rs.getString("role"));
            }
        }
    }
}
