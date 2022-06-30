package io.kyso;

import org.bson.Document;

public class Tag {
    public String id;
    public String name;

    public Tag(Document document) {
        this.setId(document.getString("id"));
        this.setName(document.getString("name"));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
