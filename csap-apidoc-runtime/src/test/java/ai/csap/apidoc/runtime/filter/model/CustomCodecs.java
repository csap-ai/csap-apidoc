package ai.csap.apidoc.runtime.filter.model;

import java.lang.reflect.Type;

import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.writer.ObjectWriter;

/**
 * 黄金样本用:自定义序列化器,写出 {"a":..,"team":..}
 */
public final class CustomCodecs {

    private CustomCodecs() {
    }

    public static final class Fastjson implements ObjectSerializer {
        @Override
        public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) {
            Exotic.Custom c = (Exotic.Custom) object;
            serializer.out.write("{\"a\":");
            serializer.write(c.getA());
            serializer.out.write(",\"team\":");
            serializer.write(c.getTeam());
            serializer.out.write('}');
        }
    }

    public static final class Fastjson2 implements ObjectWriter<Object> {
        @Override
        public void write(JSONWriter writer, Object object, Object fieldName, Type fieldType, long features) {
            Exotic.Custom c = (Exotic.Custom) object;
            writer.startObject();
            writer.writeName("a");
            writer.writeColon();
            writer.writeString(c.getA());
            writer.writeName("team");
            writer.writeColon();
            writer.getObjectWriter(Team.class).write(writer, c.getTeam(), "team", Team.class, 0);
            writer.endObject();
        }
    }
}
