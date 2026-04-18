package ai.csap.validation.factory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 过滤的实体类.
 * <p>Created on 2019/12/5
 *
 * @author yangchengfu
 * @since 1.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FilterClassParam {
    /**
     * 过滤的类型
     */
    private Class<?> type;
    /**
     * 包含
     */
    private Set<String> includes = new HashSet<>();
    /**
     * 不包含
     */
    private Set<String> excludes = new HashSet<>();
    /**
     * 是否返回操作过
     */
    private boolean response = false;

    public FilterClassParam type(Class<?> type) {
        this.type = type;
        return this;
    }

    public Set<String> getIncludes() {
        return includes == null ? new HashSet<>() : includes;
    }

    public Set<String> getExcludes() {
        return excludes == null ? new HashSet<>() : excludes;
    }

    public boolean containsIncludes(String key) {
        return getIncludes().contains(key);
    }

    public boolean containsExcludes(String key) {
        return getExcludes().contains(key);
    }

    public FilterClassParam addIncludes(String string) {
        getIncludes().add(string);
        return this;
    }

    public FilterClassParam addIncludes(Collection<? extends String> collection) {
        getIncludes().addAll(collection);
        return this;
    }

    public FilterClassParam addExcludes(String string) {
        if (!containsIncludes(string)) {
            getExcludes().add(string);
        }
        return this;
    }

    public FilterClassParam addExcludes(Collection<? extends String> collection) {
        getExcludes().addAll(collection);
        return this;
    }

    public String[] getInclude() {
        if (includes == null) {
            includes = new HashSet<>();
        }
        return includes.toArray(new String[]{});
    }

    public String[] getExclude() {
        if (excludes == null) {
            excludes = new HashSet<>();
        }
        return excludes.toArray(new String[]{});
    }

}
