package ai.csap.apidoc.runtime.filter.model;

public class Team {
    private long id;
    private String name;
    private String secret = "s";

    public Team() {
    }

    public Team(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSecret() {
        return secret;
    }
}
