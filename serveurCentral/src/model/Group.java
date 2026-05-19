package model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class Group {
    private int id;
    private String name;
    private Timestamp createdAt;
    private List<User> members;

    public Group(int id, String name, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.members = new ArrayList<>();
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public Timestamp getCreatedAt() { return createdAt; }
    public List<User> getMembers() { return members; }
    public void setMembers(List<User> members) { this.members = members; }
}
