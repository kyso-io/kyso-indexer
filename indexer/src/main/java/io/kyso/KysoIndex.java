package io.kyso;

import java.util.ArrayList;

public class KysoIndex {
    private String title;
    private String type;
    private String entityId;
    private String link;
    private String organizationSlug;
    private String teamSlug;
    private ArrayList<String> people;
    private ArrayList<String> tags;
    private String content;
    private int version;
    private String filePath;
    private boolean isPublic;

    public KysoIndex() { }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getOrganizationSlug() {
        return organizationSlug;
    }

    public void setOrganizationSlug(String organizationSlug) {
        this.organizationSlug = organizationSlug;
    }

    public String getTeamSlug() {
        return teamSlug;
    }

    public void setTeamSlug(String teamSlug) {
        this.teamSlug = teamSlug;
    }

    public ArrayList<String> getPeople() {
        return people;
    }

    public void setPeople(ArrayList<String> people) {
        this.people = people;
    }

    public ArrayList<String> getTags() {
        return tags;
    }

    public void setTags(ArrayList<String> tags) {
        this.tags = tags;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setIsPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    @Override
    public String toString() {
        return "KysoIndex{" +
                "title='" + title != null ? title : "null" + '\'' +
                ", type='" + type != null ? type : "null" + '\'' +
                ", entityId='" + entityId != null ? entityId : "null" + '\'' +
                ", link='" + link != null ? link : "null" + '\'' +
                ", organizationSlug='" + organizationSlug != null ? organizationSlug : "null" + '\'' +
                ", teamSlug='" + teamSlug != null ? teamSlug : "null" + '\'' +
                ", people='" + people != null ? people.toString() : "null" + '\'' +
                ", tags='" + tags != null ? tags.toString() : "null" + '\'' +
                ", content='" + content != null ? "content" : "null" + '\'' +
                ", version=" + version != null ? "version" : "null" +
                ", filePath='" + filePath != null ? filePath : "null" + '\'' +
                ", isPublic=" + isPublic != null ? "true" : "false" +
                '}';
    }
}
