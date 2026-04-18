package ai.csap.example.model;

import ai.csap.apidoc.annotation.EnumMessage;
import ai.csap.apidoc.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Product Category Enum
 * 
 * Another example of enum with @EnumValue and @EnumMessage
 * 
 * @author CSAP Team
 */
@AllArgsConstructor
@Getter
public enum ProductCategory {
    
    ELECTRONICS(1, "电子产品"),
    CLOTHING(2, "服装"),
    FOOD(3, "食品"),
    BOOKS(4, "图书"),
    SPORTS(5, "体育用品");

    /**
     * Category code - stored in database
     */
    @EnumValue
    private final Integer code;

    /**
     * Category name - for display
     */
    @EnumMessage
    private final String name;
}


