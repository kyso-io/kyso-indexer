package io.kyso.api;

public class Storage {
    private String name;
    private double consumedSpaceKb;
    private double consumedSpaceMb;
    private double consumedSpaceGb;

    public Storage() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getConsumedSpaceKb() {
        return consumedSpaceKb;
    }

    public void setConsumedSpaceKb(double consumedSpaceKb) {
        this.consumedSpaceKb = consumedSpaceKb;
    }

    public double getConsumedSpaceMb() {
        return consumedSpaceMb;
    }

    public void setConsumedSpaceMb(double consumedSpaceMb) {
        this.consumedSpaceMb = consumedSpaceMb;
    }

    public double getConsumedSpaceGb() {
        return consumedSpaceGb;
    }

    public void setConsumedSpaceGb(double consumedSpaceGb) {
        this.consumedSpaceGb = consumedSpaceGb;
    }
}
