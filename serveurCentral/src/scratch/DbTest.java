package scratch;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DbTest {
    public static void main(String[] args) {
        String[] urls = {
            "jdbc:mysql://localhost:3306/chatapp",
            "jdbc:mysql://127.0.0.1:3306/chatapp",
            "jdbc:mysql://100.104.161.251:3306/chatapp"
        };

        for (String url : urls) {
            System.out.println("Testing connection to: " + url);
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                try (Connection conn = DriverManager.getConnection(url, "root", "")) {
                    System.out.println("✅ Success connecting to " + url);
                    return;
                }
            } catch (Exception e) {
                System.out.println("❌ Failed: " + e.getMessage());
            }
        }

        System.out.println("\nTrying to connect to localhost without database to create 'chatapp'...");
        try {
            String baseUrl = "jdbc:mysql://localhost:3306/";
            try (Connection conn = DriverManager.getConnection(baseUrl, "root", "")) {
                System.out.println("✅ Connected to localhost server. Creating 'chatapp' database...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS chatapp");
                    System.out.println("✅ Database 'chatapp' created/verified successfully!");
                    
                    // Verify connection to the new database
                    try (Connection conn2 = DriverManager.getConnection("jdbc:mysql://localhost:3306/chatapp", "root", "")) {
                        System.out.println("✅ Verified connection to new database!");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Failed to create database on localhost: " + e.getMessage());
        }
    }
}
