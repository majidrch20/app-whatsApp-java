package dao;

import model.Group;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupDao {

    public int createGroup(String name, int creatorId, List<Integer> memberIds) {
        String sql = "INSERT INTO `groups` (name) VALUES (?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setString(1, name);
            ps.executeUpdate();
            
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int groupId = rs.getInt(1);
                
                // Add creator if not in list
                if (!memberIds.contains(creatorId)) {
                    memberIds.add(creatorId);
                }
                
                String sqlMem = "INSERT INTO group_members (group_id, user_id) VALUES (?, ?)";
                try (PreparedStatement psMem = conn.prepareStatement(sqlMem)) {
                    for (int memberId : memberIds) {
                        psMem.setInt(1, groupId);
                        psMem.setInt(2, memberId);
                        psMem.addBatch();
                    }
                    psMem.executeBatch();
                }
                return groupId;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    public List<Group> getGroupsForUser(int userId) {
        List<Group> groups = new ArrayList<>();
        String sql = "SELECT g.id, g.name, g.created_at " +
                     "FROM `groups` g " +
                     "JOIN group_members gm ON gm.group_id = g.id " +
                     "WHERE gm.user_id = ?";
                     
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                groups.add(new Group(rs.getInt("id"), rs.getString("name"), rs.getTimestamp("created_at")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return groups;
    }

    public List<Integer> getGroupMemberIds(int groupId) {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT user_id FROM group_members WHERE group_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ids.add(rs.getInt("user_id"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ids;
    }

    public boolean addMemberToGroup(int groupId, int userId) {
        String sql = "INSERT IGNORE INTO group_members (group_id, user_id) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean removeMemberFromGroup(int groupId, int userId) {
        String sql = "DELETE FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getGroupName(int groupId) {
        String sql = "SELECT name FROM `groups` WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Groupe";
    }
}
