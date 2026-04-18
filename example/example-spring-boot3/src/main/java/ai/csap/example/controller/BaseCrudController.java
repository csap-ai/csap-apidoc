package ai.csap.example.controller;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.example.model.Response;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD 抽象基类 Controller
 *
 * 提供通用的增删改查方法，子类继承时指定实体类型
 *
 * @param <T> 实体类型
 * @param <ID> 主键类型
 * @author CSAP Team
 */
@Api(value = "CRUD基类", description = "通用增删改查基类")
public abstract class BaseCrudController<T, ID> {

    /**
     * 创建实体
     */
    @PostMapping
    @ApiOperation(value = "创建", description = "创建新实体")
    public Response<T> create(@RequestBody T entity) {
        // 由子类实现具体逻辑
        return doCreate(entity);
    }

    /**
     * 根据ID获取实体
     */
    @GetMapping("/{id}")
    @ApiOperation(value = "获取详情", description = "根据ID获取实体详情")
    public Response<T> getById(@PathVariable ID id) {
        return doGetById(id);
    }

    /**
     * 更新实体
     */
    @PutMapping("/{id}")
    @ApiOperation(value = "更新", description = "更新实体信息")
    public Response<T> update(@PathVariable ID id, @RequestBody T entity) {
        return doUpdate(id, entity);
    }

    /**
     * 删除实体
     */
    @DeleteMapping("/{id}")
    @ApiOperation(value = "删除", description = "根据ID删除实体")
    public Response<Boolean> delete(@PathVariable ID id) {
        return doDelete(id);
    }

    /**
     * 查询列表
     */
    @GetMapping
    @ApiOperation(value = "查询列表", description = "查询实体列表")
    public Response<List<T>> list() {
        return doList();
    }

    /**
     * 批量创建
     */
    @PostMapping("/batch")
    @ApiOperation(value = "批量创建", description = "批量创建实体")
    public Response<List<T>> batchCreate(@RequestBody List<T> entities) {
        return doBatchCreate(entities);
    }

    // 抽象方法，由子类实现
    protected abstract Response<T> doCreate(T entity);
    protected abstract Response<T> doGetById(ID id);
    protected abstract Response<T> doUpdate(ID id, T entity);
    protected abstract Response<Boolean> doDelete(ID id);
    protected abstract Response<List<T>> doList();
    protected abstract Response<List<T>> doBatchCreate(List<T> entities);
}

