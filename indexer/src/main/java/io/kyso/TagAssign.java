package io.kyso;

import org.bson.Document;

public class TagAssign {
    public String id;
    public String tagId;
    public String entityId;
    public String type;

    public TagAssign(Document document) {
        this.setId(document.getString("id"));
        this.setTagId(document.getString("tag_id"));
        this.setEntityId(document.getString("entity_id"));
        this.setType(document.getString("type"));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTagId() {
        return tagId;
    }

    public void setTagId(String tagId) {
        this.tagId = tagId;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
