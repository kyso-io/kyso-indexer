package io.kyso;

import java.util.ArrayList;
import java.util.Date;

import org.bson.Document;

public class Report {
    private String id;
    private String sluglifiedName;
    private String userId;
    private ArrayList<String> authorIds;
    private String teamId;
    private String title;
    private Date createdAt;
    private Date updatedAt;

    public Report(Document document) {
        this.setId(document.getString("id"));
        this.setSluglifiedName(document.getString("sluglified_name"));
        this.setUserId(document.getString("user_id"));
        this.setAuthorIds((ArrayList<String>) document.get("author_ids"));
        this.setTeamId(document.getString("team_id"));
        this.setTitle(document.getString("title"));
        this.setCreatedAt(document.getDate("created_at"));
        this.setUpdatedAt(document.getDate("updated_at"));
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public ArrayList<String> getAuthorIds() {
        return authorIds;
    }

    public void setAuthorIds(ArrayList<String> authorIds) {
        this.authorIds = authorIds;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

}
