package ai.csap.apidoc.runtime.filter.model;

import com.fasterxml.jackson.annotation.JsonView;

/**
 * 黄金样本用:{@code @JsonView}
 */
public class Viewed {

    public interface Summary {
    }

    @JsonView(Summary.class)
    private String a = "a";

    @JsonView(Summary.class)
    private Team team = new Team(1, "x");

    private String c = "c";

    public String getA() {
        return a;
    }

    public Team getTeam() {
        return team;
    }

    public String getC() {
        return c;
    }
}
