package ai.csap.apidoc;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import ai.csap.apidoc.util.ExceptionUtils;

/**
 * @Author ycf
 * @Date 2022/4/26 5:51 PM
 * @Version 1.0
 */
public final class StaticFinal {
    public static final List<Class<? extends Annotation>> MAPPING_NAMES = Lists.newLinkedList();
    public static final Map<String, List<HttpMethod>> MAPPING_NAMES_TYPE = Maps.newHashMap();

    static {
        MAPPING_NAMES.add(PostMapping.class);
        MAPPING_NAMES.add(DeleteMapping.class);
        MAPPING_NAMES.add(PutMapping.class);
        MAPPING_NAMES.add(GetMapping.class);
        MAPPING_NAMES.add(PatchMapping.class);
        MAPPING_NAMES.add(RequestMapping.class);
        MAPPING_NAMES_TYPE.put(PostMapping.class.getName(), Lists.newArrayList(HttpMethod.POST));
        MAPPING_NAMES_TYPE.put(DeleteMapping.class.getName(), Lists.newArrayList(HttpMethod.DELETE));
        MAPPING_NAMES_TYPE.put(PutMapping.class.getName(), Lists.newArrayList(HttpMethod.PUT));
        MAPPING_NAMES_TYPE.put(PatchMapping.class.getName(), Lists.newArrayList(HttpMethod.PATCH));
        MAPPING_NAMES_TYPE.put(RequestMapping.class.getName(), Lists.newArrayList(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE));
        MAPPING_NAMES_TYPE.put(GetMapping.class.getName(), Lists.newArrayList(HttpMethod.GET));
    }

    public static List<HttpMethod> findMethod(String keyName) {
        if (!MAPPING_NAMES_TYPE.containsKey(keyName)) {
            throw ExceptionUtils.mpe("未匹配到信息" + keyName);
        }
        return MAPPING_NAMES_TYPE.get(keyName);
    }
}
