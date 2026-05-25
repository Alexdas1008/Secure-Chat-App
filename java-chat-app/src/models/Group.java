package models;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a chat group.
 */
public class Group implements Serializable {

    private static final long serialVersionUID = 3L;

    private String name;
    private String description;
    private String createdBy;
    private LocalDateTime createdAt;
    private Set<String> members;    // set of usernames
    private Set<String> admins;     // subset of members with admin rights

    public Group(String name, String description, String createdBy) {
        this.name        = name;
        this.description = description;
        this.createdBy   = createdBy;
        this.createdAt   = LocalDateTime.now();
        this.members     = new HashSet<>();
        this.admins      = new HashSet<>();
        this.members.add(createdBy);
        this.admins.add(createdBy);
    }

    public synchronized void addMember(String username) {
        members.add(username);
    }

    public synchronized boolean removeMember(String username) {
        admins.remove(username);
        return members.remove(username);
    }

    public synchronized boolean isMember(String username) {
        return members.contains(username);
    }

    public synchronized boolean isAdmin(String username) {
        return admins.contains(username);
    }

    public synchronized void promoteToAdmin(String username) {
        if (members.contains(username)) admins.add(username);
    }

    public String getName()        { return name; }
    public String getDescription() { return description; }
    public String getCreatedBy()   { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public synchronized Set<String> getMembers() { return new HashSet<>(members); }
    public synchronized Set<String> getAdmins()  { return new HashSet<>(admins); }
    public synchronized int getMemberCount()     { return members.size(); }

    @Override
    public String toString() {
        return "Group{name='" + name + "', members=" + members.size() + "}";
    }
}
