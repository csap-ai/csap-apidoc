package ai.csap.example.controller;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.example.model.Product;
import ai.csap.example.model.Response;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Product Management Controller
 * <p>
 * Demonstrates:
 * - Complex data types (BigDecimal)
 * - Different parameter types
 * - Batch operations
 *
 * @author CSAP Team
 */
@RestController
@RequestMapping("/api/products")
@Api(value = "产品管理", description = "产品相关接口")
public class ProductController {

    /**
     * List products
     */
    @GetMapping("listProducts")
    @ApiOperation(value = "获取产品列表", description = "查询产品列表，支持分页和筛选")
    public Response<List<Product>> listProducts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean onSale
    ) {
        // Mock data
        List<Product> products = new ArrayList<>();
        Product product = new Product();
        product.setId(2001L);
        product.setName("iPhone 15 Pro");
        product.setDescription("最新款苹果手机");
        product.setPrice(new BigDecimal("7999.00"));
        product.setStock(100);
        product.setCategoryId(1L);
        product.setOnSale(true);
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());
        products.add(product);

        return Response.success(products);
    }

    /**
     * Get product by ID
     */
    @GetMapping("/getProduct/{id}")
    @ApiOperation(value = "获取产品详情", description = "根据产品ID获取详细信息")
    public Response<Product> getProduct(@PathVariable Long id) {
        Product product = new Product();
        product.setId(id);
        product.setName("iPhone 15 Pro");
        product.setDescription("最新款苹果手机");
        product.setPrice(new BigDecimal("7999.00"));
        product.setStock(100);
        product.setCategoryId(1L);
        product.setOnSale(true);
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());

        return Response.success(product);
    }

    /**
     * Create product
     */
    @PostMapping("createProduct")
    @ApiOperation(value = "创建产品", description = "创建新产品")
    public Response<Product> createProduct(@RequestBody Product product) {
        product.setId(System.currentTimeMillis());
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());

        return Response.success("产品创建成功", product);
    }

    /**
     * Update product
     */
    @PutMapping("/updateProduct/{id}")
    @ApiOperation(value = "更新产品", description = "更新产品信息")
    public Response<Product> updateProduct(
            @PathVariable Long id,
            @RequestBody Product product
    ) {
        product.setId(id);
        product.setUpdateTime(LocalDateTime.now());

        return Response.success("产品更新成功", product);
    }

    /**
     * Delete product
     */
    @DeleteMapping("/deleteProduct/{id}")
    @ApiOperation(value = "删除产品", description = "删除指定产品")
    public Response<Void> deleteProduct(@PathVariable Long id) {
        return Response.success("产品删除成功", null);
    }

    /**
     * Update stock
     */
    @PatchMapping("/updateStock/{id}/stock")
    @ApiOperation(value = "更新库存", description = "更新产品库存数量")
    public Response<Product> updateStock(
            @PathVariable Long id,
            @RequestParam Integer stock
    ) {
        Product product = new Product();
        product.setId(id);
        product.setStock(stock);
        product.setUpdateTime(LocalDateTime.now());

        return Response.success("库存更新成功", product);
    }

    /**
     * Batch delete
     */
    @DeleteMapping("/batch")
    @ApiOperation(value = "批量删除产品", description = "批量删除多个产品")
    public Response<Void> batchDelete(@RequestBody List<Long> ids) {
        return Response.success("批量删除成功，共删除 " + ids.size() + " 个产品", null);
    }
}

