package ai.csap.apidoc.runtime.filter.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

/**
 * 黄金样本用:各库的特殊注解与特殊结构
 */
public class Exotic {

    @JsonUnwrapped(prefix = "u_")
    private Team unwrapped = new Team(20, "U");

    private Shape shape = new Shape();

    private Ident ident = new Ident();

    private Ident identAgain = ident;

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    private Team typed = new TeamWithExtra();

    private WithAny any = new WithAny();

    private Custom custom = new Custom();

    private Pub pub = new Pub();

    @com.alibaba.fastjson.annotation.JSONField(format = "yyyy")
    @com.alibaba.fastjson2.annotation.JSONField(format = "yyyy")
    private List<Date> fmtDates = new ArrayList<>(Arrays.asList(new Date(1700000000000L)));

    private List<Object> mixed = new ArrayList<>(Arrays.asList(new Team(21, "V"), Arrays.asList(new Team(22, "W")),
            new Team[] {new Team(23, "X")}, "s", 1, null));

    public Team getUnwrapped() {
        return unwrapped;
    }

    public Shape getShape() {
        return shape;
    }

    public Ident getIdent() {
        return ident;
    }

    public Ident getIdentAgain() {
        return identAgain;
    }

    public Team getTyped() {
        return typed;
    }

    public WithAny getAny() {
        return any;
    }

    public Custom getCustom() {
        return custom;
    }

    public Pub getPub() {
        return pub;
    }

    public List<Date> getFmtDates() {
        return fmtDates;
    }

    public List<Object> getMixed() {
        return mixed;
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public static class Shape {
        private int x = 1;
        private String secret = "s";
        private Team team = new Team(24, "Y");

        public int getX() {
            return x;
        }

        public String getSecret() {
            return secret;
        }

        public Team getTeam() {
            return team;
        }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class)
    public static class Ident {
        private String id = "i";
        private String secret = "s";

        public String getId() {
            return id;
        }

        public String getSecret() {
            return secret;
        }
    }

    public static class WithAny {
        private String name = "n";
        private Map<String, Object> extras = new LinkedHashMap<>();

        public WithAny() {
            extras.put("secret", "s");
            extras.put("team", new Team(25, "Z"));
        }

        public String getName() {
            return name;
        }

        @JsonAnyGetter
        public Map<String, Object> getExtras() {
            return extras;
        }
    }

    /**
     * 自定义过滤器 id(Jackson)、自定义序列化器(fastjson / fastjson2)
     */
    @JsonFilter("customFilter")
    @com.alibaba.fastjson.annotation.JSONType(serializer = CustomCodecs.Fastjson.class)
    @com.alibaba.fastjson2.annotation.JSONType(serializer = CustomCodecs.Fastjson2.class)
    public static class Custom {
        private String a = "a";
        private String secret = "s";
        private Team team = new Team(26, "C");

        public String getA() {
            return a;
        }

        public String getSecret() {
            return secret;
        }

        public Team getTeam() {
            return team;
        }
    }

    /**
     * 只有 public 字段
     */
    public static class Pub {
        public int id = 27;
        public String secret = "s";
        public Team team = new Team(28, "P");
    }
}
