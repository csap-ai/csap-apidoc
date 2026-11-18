package ai.csap.apidoc.runtime.filter.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 黄金样本用:同一个对象 / 集合 / Map 实例出现多次(fastjson 的循环引用检测会输出 $ref)
 */
public class Shared {
    private final Team team = new Team(30, "S");
    private final List<Team> list = new ArrayList<>(Arrays.asList(team, new Team(31, "T"), team));
    private final Map<String, Team> map = new LinkedHashMap<>();
    private final List<Object> nested = new ArrayList<>();
    // 与上面同一个实例;写成字段使 Jackson 的属性顺序固定(只有 getter 的属性顺序取决于反射返回的方法顺序)
    private final Team teamAgain = team;
    private final List<Team> listAgain = list;
    private final Map<String, Team> mapAgain = map;

    public Shared() {
        map.put("x", team);
        map.put("y", new Team(32, "U"));
        nested.add(list);
        nested.add(list);
        nested.add(map);
    }

    public Team getTeam() {
        return team;
    }

    public Team getTeamAgain() {
        return teamAgain;
    }

    public List<Team> getList() {
        return list;
    }

    public List<Team> getListAgain() {
        return listAgain;
    }

    public Map<String, Team> getMap() {
        return map;
    }

    public Map<String, Team> getMapAgain() {
        return mapAgain;
    }

    public List<Object> getNested() {
        return nested;
    }
}
