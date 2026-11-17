package ai.csap.apidoc.runtime.filter.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON 名是下划线(三个库的注解都写上),规则写驼峰
 */
public class Match {
    private long id = 7;
    @JsonProperty("league_id")
    @com.alibaba.fastjson.annotation.JSONField(name = "league_id")
    @com.alibaba.fastjson2.annotation.JSONField(name = "league_id")
    private long leagueId = 3;
    private Team home = new Team(1, "A");
    private Team away = new Team(2, "B");
    @JsonProperty("by_half")
    @com.alibaba.fastjson.annotation.JSONField(name = "by_half")
    @com.alibaba.fastjson2.annotation.JSONField(name = "by_half")
    private Map<String, Team> byHalf = new LinkedHashMap<>(Map.of("h1", new Team(1, "A")));
    private String internal = "x";

    public long getId() {
        return id;
    }

    public long getLeagueId() {
        return leagueId;
    }

    public Team getHome() {
        return home;
    }

    public Team getAway() {
        return away;
    }

    public Map<String, Team> getByHalf() {
        return byHalf;
    }

    public String getInternal() {
        return internal;
    }
}
