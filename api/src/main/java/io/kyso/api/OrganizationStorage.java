package io.kyso.api;

import java.util.List;

public class OrganizationStorage extends Storage {
    private List<Storage> teams;

    public List<Storage> getTeams() {
        return teams;
    }

    public void setTeams(List<Storage> teams) {
        this.teams = teams;
    }
}
