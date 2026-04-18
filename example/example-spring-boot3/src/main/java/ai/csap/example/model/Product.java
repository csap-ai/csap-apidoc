package ai.csap.example.model;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Product Entity
 *
 * @author CSAP Team
 */
@Data
@ApiModel(description = "产品实体")
public class Product {

    @ApiModelProperty(value = "产品ID", example = "2001")
    private Long id;

    @ApiModelProperty(value = "产品名称", required = true, example = "iPhone 15 Pro")
    @NotBlank(message = "产品名称不能为空")
    @Size(max = 100, message = "产品名称不能超过100个字符")
    private String name;

    @ApiModelProperty(value = "产品描述", example = "最新款苹果手机")
    @Size(max = 500, message = "产品描述不能超过500个字符")
    private String description;

    @ApiModelProperty(value = "价格", required = true, example = "7999.00")
    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    @Digits(integer = 10, fraction = 2, message = "价格格式不正确")
    private BigDecimal price;

    @ApiModelProperty(value = "库存数量", example = "100")
    @Min(value = 0, message = "库存数量不能为负数")
    private Integer stock;

    @ApiModelProperty(value = "分类ID", required = true, example = "1")
    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @ApiModelProperty(value = "是否上架", example = "true")
    private Boolean onSale;

    @ApiModelProperty(value = "创建时间", example = "2024-01-01T10:00:00")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间", example = "2024-01-01T10:00:00")
    private LocalDateTime updateTime;
}

