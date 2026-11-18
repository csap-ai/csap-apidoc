package ai.csap.apidoc.runtime.filter.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 黄金样本用:各种字段类型、注解、共享引用
 */
public class Rich {
    private Integer nullInt;
    private String nullStr;
    private String text = "t\"x</script>";
    private long big = 9007199254740993L;
    private BigDecimal amount = new BigDecimal("12.50");
    private Color color = Color.GREEN;
    private boolean flag = true;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @com.alibaba.fastjson.annotation.JSONField(format = "yyyy-MM-dd")
    @com.alibaba.fastjson2.annotation.JSONField(format = "yyyy-MM-dd")
    private Date day = new Date(1700000000000L);
    private long[] longs = {1, 2};
    private Team[] teamArray = {new Team(5, "E"), null};
    private List<List<Team>> nested = new ArrayList<>(Arrays.asList(
            new ArrayList<>(Arrays.asList(new Team(6, "F"))), new ArrayList<>()));
    private Map<String, List<Team>> groups = new LinkedHashMap<>();
    private Map<Integer, Team> byNumber = new LinkedHashMap<>();
    private Set<Team> teamSet = new LinkedHashSet<>();
    private Object anything = new TeamWithExtra();
    private Team shared;
    private Team sharedAgain;
    private Team nullTeam;
    private List<Team> emptyList = new ArrayList<>();
    private List<Date> dates = new ArrayList<>(Arrays.asList(new Date(1700000000000L)));
    @JsonIgnore
    @com.alibaba.fastjson.annotation.JSONField(serialize = false)
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    private String hidden = "h";
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String nonNull;
    private Map<String, Object> loose = new LinkedHashMap<>();

    public Rich() {
        groups.put("g1", new ArrayList<>(Arrays.asList(new Team(7, "G"), new Team(8, "H"))));
        byNumber.put(1, new Team(9, "I"));
        teamSet.add(new Team(10, "J"));
        Team s = new Team(11, "K");
        shared = s;
        sharedAgain = s;
        loose.put("secret", "top");
        loose.put("team", new Team(12, "L"));
        loose.put("list", new ArrayList<>(Arrays.asList(new Team(13, "M"), "str", 3)));
    }

    public Integer getNullInt() {
        return nullInt;
    }

    public String getNullStr() {
        return nullStr;
    }

    public String getText() {
        return text;
    }

    public long getBig() {
        return big;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Color getColor() {
        return color;
    }

    public boolean isFlag() {
        return flag;
    }

    public Date getDay() {
        return day;
    }

    public long[] getLongs() {
        return longs;
    }

    public Team[] getTeamArray() {
        return teamArray;
    }

    public List<List<Team>> getNested() {
        return nested;
    }

    public Map<String, List<Team>> getGroups() {
        return groups;
    }

    public Map<Integer, Team> getByNumber() {
        return byNumber;
    }

    public Set<Team> getTeamSet() {
        return teamSet;
    }

    public Object getAnything() {
        return anything;
    }

    public Team getShared() {
        return shared;
    }

    public Team getSharedAgain() {
        return sharedAgain;
    }

    public Team getNullTeam() {
        return nullTeam;
    }

    public List<Team> getEmptyList() {
        return emptyList;
    }

    public List<Date> getDates() {
        return dates;
    }

    public String getHidden() {
        return hidden;
    }

    public String getNonNull() {
        return nonNull;
    }

    public Map<String, Object> getLoose() {
        return loose;
    }
}
