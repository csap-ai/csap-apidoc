package ai.csap.example.controller;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.apidoc.annotation.ApiProperty;
import ai.csap.example.model.Product;
import ai.csap.example.model.Response;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product API Interface
 * <p>
 * Defines all product-related API endpoints with documentation annotations
 *
 * @author CSAP Team
 */
@Api(value = "产品管理", description = "产品相关接口")
public interface ProductApi {

    /**
     * List products
     */
    @GetMapping("listProducts")
    @ApiOperation(value = "获取产品列表", description = "查询产品列表，支持分页和筛选")
    Response<List<Product>> listProducts(
            @ApiProperty(value = "页码", example = "1", defaultValue = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @ApiProperty(value = "每页数量", example = "10", defaultValue = "10")
            @RequestParam(defaultValue = "10") Integer pageSize,
            @ApiProperty(value = "分类ID", example = "1")
            @RequestParam(required = false) Long categoryId,
            @ApiProperty(value = "最低价格", example = "100.00")
            @RequestParam(required = false) BigDecimal minPrice,
            @ApiProperty(value = "最高价格", example = "10000.00")
            @RequestParam(required = false) BigDecimal maxPrice,
            @ApiProperty(value = "是否在售", example = "true")
            @RequestParam(required = false) Boolean onSale
    );

    /**
     * Get product by ID
     */
    @GetMapping("/getProduct/{id}")
    @ApiOperation(value = "获取产品详情", description = "根据产品ID获取详细信息")
    Response<Product> getProduct(
            @ApiProperty(value = "产品ID", example = "2001", required = true)
            @PathVariable Long id
    );

    /**
     * Create product
     */
    @PostMapping("createProduct")
    @ApiOperation(value = "创建产品", description = "创建新产品")
    Response<Product> createProduct(@Valid @RequestBody Product product);

    /**
     * Update product
     */
    @PutMapping("/updateProduct/{id}")
    @ApiOperation(value = "更新产品", description = "更新产品信息")
    Response<Product> updateProduct(
            @ApiProperty(value = "产品ID", example = "2001", required = true)
            @PathVariable Long id,
            @Valid @RequestBody Product product
    );

    /**
     * Delete product
     */
    @DeleteMapping("/deleteProduct/{id}")
    @ApiOperation(value = "删除产品", description = "删除指定产品")
    Response<Void> deleteProduct(
            @ApiProperty(value = "产品ID", example = "2001", required = true)
            @PathVariable Long id
    );

    /**
     * Update stock
     */
    @PatchMapping("/updateStock/{id}/stock")
    @ApiOperation(value = "更新库存", description = "更新产品库存数量")
    Response<Product> updateStock(
            @ApiProperty(value = "产品ID", example = "2001", required = true)
            @PathVariable Long id,
            @ApiProperty(value = "库存数量", example = "100", required = true)
            @RequestParam Integer stock
    );

    /**
     * Batch delete
     */
    @DeleteMapping("/batch")
    @ApiOperation(value = "批量删除产品", description = "批量删除多个产品")
    Response<Void> batchDelete(@RequestBody List<Long> ids);
}

