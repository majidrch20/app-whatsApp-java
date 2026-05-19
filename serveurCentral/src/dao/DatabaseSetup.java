package dao;

import java.sql.Connection;
import java.sql.Statement;

public class DatabaseSetup {
    public static void main(String[] args) {
        System.out.println("--- Création des tables de groupes ---");
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `groups` (" +
                               "id INT AUTO_INCREMENT PRIMARY KEY, " +
                               "name VARCHAR(255) NOT NULL, " +
                               "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                               ")");
            
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS group_members (" +
                               "group_id INT NOT NULL, " +
                               "user_id INT NOT NULL, " +
                               "PRIMARY KEY (group_id, user_id), " +
                               "FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE, " +
                               "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                               ")");
            
            try {
                stmt.executeUpdate("ALTER TABLE messages ADD COLUMN group_id INT DEFAULT NULL");
                System.out.println("Colonne group_id ajoutée.");
            } catch (Exception e) {
                System.out.println("Info: La colonne group_id existe peut-être déjà.");
            }
            
            try {
                stmt.executeUpdate("ALTER TABLE messages MODIFY COLUMN receiver_id INT DEFAULT NULL");
                System.out.println("Colonne receiver_id modifiée pour accepter NULL.");
            } catch (Exception e) {
                System.out.println("Info: Impossible de modifier receiver_id.");
            }

            try {
                stmt.executeUpdate("ALTER TABLE messages ADD FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE");
                System.out.println("Clé étrangère group_id ajoutée.");
            } catch (Exception e) {
                System.out.println("Info: La clé étrangère group_id existe peut-être déjà.");
            }
            
            System.out.println("✅ Base de données mise à jour avec succès !");
            
        } catch (Exception e) {
            System.err.println("❌ Erreur : " + e.getMessage());
            e.printStackTrace();
        }
    }
}
