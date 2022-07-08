package io.kyso;

import org.bson.Document;

public class Team {

    private String id;
    private String sluglifiedName;
    private String name;
    private String organizationId;
    private TeamVisibility visibility;

    public Team() {
    }

    public Team(Document document) {
        this.setId(document.getString("id"));
        this.setSluglifiedName(document.getString("sluglified_name"));
        this.setName(document.getString("display_name"));
        this.setOrganizationId(document.getString("organization_id"));
        TeamVisibility visibility;
        switch (document.getString("visibility")) {
            case "public":
                visibility = TeamVisibility.PUBLIC;
                break;
            case "protected":
                visibility = TeamVisibility.PROTECTED;
                break;
            case "private":
                visibility = TeamVisibility.PRIVATE;
                break;
            default:
                visibility = TeamVisibility.PUBLIC;
                break;
        }
        this.setVisibility(visibility);
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

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public TeamVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(TeamVisibility visibility) {
        this.visibility = visibility;
    }

    public boolean isPublic() {
        return visibility == TeamVisibility.PUBLIC;
    }

}
