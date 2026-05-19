package dao;

import model.User;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDao {

    // ─────────────────────────────
    // SAVE OTP CODE (FIXÉ PROPRE)
    // ─────────────────────────────
    public void saveVerificationCode(String phone, String code) {
        User existing = searchByPhone(phone);
        if (existing != null) {
            String sql = "UPDATE users SET verification_code = ? WHERE id = ?";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code);
                ps.setInt(2, existing.getId());
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            String insert = "INSERT INTO users(phone, verification_code, verified, status) VALUES(?, ?, false, 'OFFLINE')";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps2 = conn.prepareStatement(insert)) {
                ps2.setString(1, phone);
                ps2.setString(2, code);
                ps2.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public boolean verifyCode(String phone, String code) {
        User existing = searchByPhone(phone);
        if (existing == null) return false;
        String sql = "SELECT verification_code FROM users WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, existing.getId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String dbCode = rs.getString("verification_code");
                return dbCode != null && dbCode.trim().equals(code.trim());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void clearVerificationCode(String phone) {
        User existing = searchByPhone(phone);
        if (existing != null) {
            String sql = "UPDATE users SET verification_code = NULL WHERE id = ?";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, existing.getId());
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void markVerifiedAndSetUsername(String phone, String username) {
        User existing = searchByPhone(phone);
        if (existing != null) {
            String sql = "UPDATE users SET verified = TRUE, username = ?, status = 'ONLINE' WHERE id = ?";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setInt(2, existing.getId());
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void updateStatusById(int userId, String status) {
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getIdByPhone(String phone) {
        User u = searchByPhone(phone);
        if (u != null) return u.getId();
        return -1;
    }

    public User getByPhone(String phone) {
        return searchByPhone(phone);
    }

    public User searchByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) return null;
        
        // 1. Recherche exacte d'abord
        String exactSql = "SELECT * FROM users WHERE phone = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(exactSql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("phone"),
                            rs.getString("username"),
                            rs.getString("verification_code"),
                            rs.getBoolean("verified"),
                            rs.getString("status")
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Recherche floue par les 9 derniers chiffres
        String targetDigits = normalizeDigits(phone);
        if (targetDigits.isEmpty()) return null;

        String targetLast9 = targetDigits.length() >= 9 
                ? targetDigits.substring(targetDigits.length() - 9) 
                : targetDigits;

        String sql = "SELECT * FROM users";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String dbPhone = rs.getString("phone");
                String dbDigits = normalizeDigits(dbPhone);
                if (dbDigits.isEmpty()) continue;
                String dbLast9 = dbDigits.length() >= 9 
                        ? dbDigits.substring(dbDigits.length() - 9) 
                        : dbDigits;
                if (dbLast9.equals(targetLast9)) {
                    return new User(
                            rs.getInt("id"),
                            dbPhone,
                            rs.getString("username"),
                            rs.getString("verification_code"),
                            rs.getBoolean("verified"),
                            rs.getString("status")
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String normalizeDigits(String input) {
        if (input == null) return "";
        return input.replaceAll("[^0-9]", "");
    }
}