package ai.csap.example.controller;

import ai.csap.apidoc.annotation.Api;
import ai.csap.example.model.Response;
import ai.csap.example.model.User;
import ai.csap.example.model.UserStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户 CRUD Controller
 * 
 * 继承抽象基类 BaseCrudController，指定泛型参数为 <User, Long>
 * 
 * 注意：这个类没有重写父类的 HTTP 方法（create、getById、update、delete、list、batchCreate）
 * 这些方法直接从父类继承，但泛型类型已被指定为 User
 * 
 * 测试场景：验证 getFieldsFromMethod 能否正确解析继承方法中的泛型类型
 *
 * @author CSAP Team
 */
@RestController
@RequestMapping("/api/user-crud")
@Api(value = "用户CRUD", description = "用户增删改查接口（继承自抽象基类）")
public class UserCrudController extends BaseCrudController<User, Long> {

    @Override
    protected Response<User> doCreate(User entity) {
        entity.setId(System.currentTimeMillis());
        entity.setStatus(UserStatus.ACTIVE);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return Response.success("创建成功", entity);
    }

    @Override
    protected Response<User> doGetById(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("test_user");
        user.setEmail("test@example.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return Response.success(user);
    }

    @Override
    protected Response<User> doUpdate(Long id, User entity) {
        entity.setId(id);
        entity.setUpdateTime(LocalDateTime.now());
        return Response.success("更新成功", entity);
    }

    @Override
    protected Response<Boolean> doDelete(Long id) {
        return Response.success("删除成功", true);
    }

    @Override
    protected Response<List<User>> doList() {
        List<User> users = new ArrayList<>();
        User user = new User();
        user.setId(1001L);
        user.setUsername("user1");
        user.setEmail("user1@example.com");
        user.setStatus(UserStatus.ACTIVE);
        users.add(user);
        return Response.success(users);
    }

    @Override
    protected Response<List<User>> doBatchCreate(List<User> entities) {
        for (User entity : entities) {
            entity.setId(System.currentTimeMillis());
            entity.setStatus(UserStatus.ACTIVE);
            entity.setCreateTime(LocalDateTime.now());
        }
        return Response.success("批量创建成功", entities);
    }
}

