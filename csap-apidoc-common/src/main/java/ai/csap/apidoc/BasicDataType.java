package ai.csap.apidoc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.YearMonth;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 基本数据类型枚举
 *
 * @Author ycf
 * @Date 2021/11/6 9:55 下午
 * @Version 1.0
 */
@Getter
@AllArgsConstructor
public enum BasicDataType {
    /**
     * 基本数据类型
     */
    STRING(String.class.getName(), String.class, "字符串"),
    BYTE(Byte.class.getName(), Byte.class, "字节包装类型"),
    SHORT(Short.class.getName(), Short.class, "短整型"),
    INTEGER(Integer.class.getName(), Integer.class, "数字"),
    LONG(Long.class.getName(), Long.class, "长整型"),
    FLOAT(Float.class.getName(), Float.class, "浮点型"),
    DOUBLE(Double.class.getName(), Double.class, "双精度"),
    CHARACTER(Character.class.getName(), Character.class, "字符"),
    BOOLEAN(Boolean.class.getName(), Boolean.class, "布尔类型"),
    BIGINTEGER(BigInteger.class.getName(), BigInteger.class, "大整型"),
    BIGDECIMAL(BigDecimal.class.getName(), BigDecimal.class, "金额类型"),
    DATE(Date.class.getName(), Date.class, "时间类型"),
    LOCAL_TIME(LocalTime.class.getName(), LocalTime.class, "时间"),
    TIMESTAMP(Timestamp.class.getName(), Timestamp.class, "时间戳"),
    LOCAL_DATE_TIME(LocalDateTime.class.getName(), LocalDateTime.class, "年月日时间类型"),
    LOCAL_DATE(LocalDate.class.getName(), LocalDate.class, "年月日"),
    BYTE1(byte.class.getName(), byte.class, "字节"),
    SHORT1(short.class.getName(), short.class, "短整型"),
    INT(int.class.getName(), int.class, "数字"),
    LONG1(long.class.getName(), long.class, "长整型"),
    FLOAT1(float.class.getName(), float.class, "浮点型"),
    DOUBLE1(double.class.getName(), double.class, "浮点型"),
    CHAR1(char.class.getName(), char.class, "字符串"),
    BOOLEAN1(boolean.class.getName(), boolean.class, "布尔类型"),
    YEAR(Year.class.getName(), Year.class, "年"),
    YEAR_MONTH(YearMonth.class.getName(), YearMonth.class, "年月"),
    ;
    /**
     * className 名称
     */
    private String name;
    /**
     * class类型
     */
    private Class<?> aClass;
    /**
     * 描述
     */
    private String descr;


    public static BasicDataType findByAll(String name, Class<?> cl) {
        for (BasicDataType n : values()) {
            if (n.getName().equals(name)) {
                return n;
            }
            if (n.getAClass().equals(cl)) {
                return n;
            }
        }
        return null;
    }

    /**
     * 根据名称查询
     *
     * @param name
     * @return
     */
    public static BasicDataType findByName(String name) {
        return findByAll(name, null);
    }

    /**
     * 根据class查询
     *
     * @param cl
     * @return
     */
    public static BasicDataType findByClass(Class<?> cl) {
        return findByAll(null, cl);
    }
}
