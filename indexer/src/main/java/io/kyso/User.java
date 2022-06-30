package io.kyso;

import org.bson.Document;

public class User {
    private String id;
    private String email;
    private String username;
    private String name;
    private String displayName;

    public User(Document document) {
        this.setId(document.getString("id"));
        this.setEmail(document.getString("email"));
        this.setUsername(document.getString("username"));
        this.setName(document.getString("name"));
        this.setDisplayName(document.getString("display_name"));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
