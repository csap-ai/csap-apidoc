package ai.csap.apidoc.runtime.filter.model;

public class TeamWithExtra extends Team {
    private int extra = 1;

    public TeamWithExtra() {
        super(1, "a");
    }

    public int getExtra() {
        return extra;
    }
}
