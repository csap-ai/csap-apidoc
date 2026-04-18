package ai.csap.apidoc;

import java.beans.PropertyDescriptor;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.dbutils.BeanProcessor;

/**
 * Simple camel case bean processor.
 * <p>Created on 2025/9/8
 *
 * @author ycf
 * @since 1.0
 */
public class SimpleCamelCaseBeanProcessor extends BeanProcessor {
    @Override
    protected int[] mapColumnsToProperties(ResultSetMetaData rsmd, PropertyDescriptor[] props) throws SQLException {
        int columnCount = rsmd.getColumnCount();
        int[] columnToProperty = new int[columnCount + 1];
        Map<String, Integer> propNameIndexMap = new HashMap<>();

        // 把实体类属性名（驼峰）存入 Map，键是属性名，值是属性在 props 中的索引
        for (int i = 0; i < props.length; i++) {
            propNameIndexMap.put(props[i].getName(), i);
        }

        for (int col = 1; col <= columnCount; col++) {
            String dbColumnName = rsmd.getColumnLabel(col);
            if (dbColumnName == null || dbColumnName.isEmpty()) {
                dbColumnName = rsmd.getColumnName(col);
            }
            // 下划线转驼峰（核心简化：用 split + 首字母大写拼接）
            String camelCaseName = underlineToCamel(dbColumnName);
            // 查找实体类中是否有对应驼峰名的属性，有则记录索引，无则标记为 PROPERTY_NOT_FOUND
            columnToProperty[col] = propNameIndexMap.getOrDefault(camelCaseName, PROPERTY_NOT_FOUND);
        }
        return columnToProperty;
    }

    // 下划线转驼峰：简单 split 后，首字母大写拼接
    private String underlineToCamel(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        String[] parts = str.split("_");
        StringBuilder camelCase = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            camelCase.append(Character.toUpperCase(parts[i].charAt(0)))
                    .append(parts[i].substring(1));
        }
        return camelCase.toString();
    }
}
