package scratch;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

public class QueryDb {
    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chatapp", "root", "");
            System.out.println("Connection successful!");
            
            Statement stmt = conn.createStatement();
            String[] tables = {"users", "messages", "contacts", "groups", "group_members"};
            for (String table : tables) {
                System.out.println("\n--- Content of " + table + " ---");
                try {
                    ResultSet rs = stmt.executeQuery("SELECT * FROM `" + table + "` LIMIT 20");
                    ResultSetMetaData meta = rs.getMetaData();
                    int cols = meta.getColumnCount();
                    for (int i = 1; i <= cols; i++) {
                        System.out.print(meta.getColumnName(i) + "\t");
                    }
                    System.out.println();
                    while (rs.next()) {
                        for (int i = 1; i <= cols; i++) {
                            String val = rs.getString(i);
                            if (val != null && val.length() > 50) {
                                val = val.substring(0, 50) + "...";
                            }
                            System.out.print(val + "\t");
                        }
                        System.out.println();
                    }
                } catch (Exception e) {
                    System.out.println("Error reading table " + table + ": " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
