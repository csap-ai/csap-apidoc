package ai.csap.example.controller;

import ai.csap.example.model.Product;
import ai.csap.example.model.Response;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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
public class ProductController implements ProductApi {

    @Override
    public Response<List<Product>> listProducts(
            Integer page,
            Integer pageSize,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean onSale
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

    @Override
    public Response<Product> getProduct(Long id) {
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

    @Override
    public Response<Product> createProduct(Product product) {
        product.setId(System.currentTimeMillis());
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());

        return Response.success("产品创建成功", product);
    }

    @Override
    public Response<Product> updateProduct(Long id, Product product) {
        product.setId(id);
        product.setUpdateTime(LocalDateTime.now());

        return Response.success("产品更新成功", product);
    }

    @Override
    public Response<Void> deleteProduct(Long id) {
        return Response.success("产品删除成功", null);
    }

    @Override
    public Response<Product> updateStock(Long id, Integer stock) {
        Product product = new Product();
        product.setId(id);
        product.setStock(stock);
        product.setUpdateTime(LocalDateTime.now());

        return Response.success("库存更新成功", product);
    }

    @Override
    public Response<Void> batchDelete(List<Long> ids) {
        return Response.success("批量删除成功，共删除 " + ids.size() + " 个产品", null);
    }
}

