package ai.csap.apidoc.runtime.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import ai.csap.apidoc.runtime.filter.FilterEngine;
import ai.csap.apidoc.runtime.filter.FilterMode;

class RuntimeFilterPropertiesTest {

    @Test
    void bindsEndpointsWithDotsInKeysAndYamlOff() {
        Map<String, Object> source = new HashMap<>();
        source.put("csap.apidoc.runtime.filter.mode", "csap");
        source.put("csap.apidoc.runtime.filter.engine", "fastjson2");
        source.put("csap.apidoc.runtime.filter.endpoints[com.x.UserController.get]", Boolean.FALSE);
        source.put("csap.apidoc.runtime.filter.endpoints[com.x.UserController.list]", "path");
        RuntimeFilterProperties props = new Binder(new MapConfigurationPropertySource(source))
                .bind(RuntimeFilterProperties.PREFIX, Bindable.of(RuntimeFilterProperties.class)).get();
        assertFalse(props.isEnabled());
        assertEquals(FilterEngine.FASTJSON2, props.getEngine());
        // YAML 里不加引号的 off 会被解析成布尔 false
        assertEquals(FilterMode.OFF, props.modeFor("com.x.UserController.get"));
        assertEquals(FilterMode.PATH, props.modeFor("com.x.UserController.list"));
        assertEquals(FilterMode.CSAP, props.modeFor("com.x.Other.get"));
    }

    @Test
    void defaults() {
        RuntimeFilterProperties props = new RuntimeFilterProperties();
        assertFalse(props.isEnabled());
        assertEquals(FilterMode.PATH, props.getMode());
        assertEquals(FilterEngine.AUTO, props.getEngine());
    }
}
