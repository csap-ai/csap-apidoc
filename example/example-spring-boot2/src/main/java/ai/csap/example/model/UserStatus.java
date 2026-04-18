package ai.csap.example.model;

import ai.csap.apidoc.annotation.EnumMessage;
import ai.csap.apidoc.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * User Status Enum
 * 
 * Demonstrates correct usage of @EnumValue and @EnumMessage
 * - @EnumValue marks the field that stores in database (code)
 * - @EnumMessage marks the field that provides description (message)
 * 
 * @author CSAP Team
 */
@AllArgsConstructor
@Getter
public enum UserStatus {
    
    ACTIVE(1, "激活"),
    DISABLED(2, "禁用"),
    LOCKED(3, "锁定"),
    PENDING(4, "待审核");

    /**
     * Status code - stored in database
     */
    @EnumValue
    private final Integer code;

    /**
     * Status description - for display
     */
    @EnumMessage
    private final String message;
}

