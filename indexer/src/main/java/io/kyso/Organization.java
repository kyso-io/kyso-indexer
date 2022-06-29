package io.kyso;

import org.bson.Document;

public class Organization {

    private String id;
    private String sluglifiedName;
    private String name;

    public Organization() {}

    public Organization(Document document) {
        this.setId(document.getString("id"));
        this.setSluglifiedName(document.getString("sluglified_name"));
        this.setName(document.getString("name"));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSluglifiedName() {
        return sluglifiedName;
    }

    public void setSluglifiedName(String sluglifiedName) {
        this.sluglifiedName = sluglifiedName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
