package ai.csap.example.controller;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.example.model.Response;
import ai.csap.example.model.User;
import ai.csap.example.model.UserStatus;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * User Management Controller
 * <p>
 * Demonstrates:
 * - API documentation annotations
 * - Parameter validation
 * - Different HTTP methods
 * - Query parameters and path variables
 *
 * @author CSAP Team
 */
@RestController
@RequestMapping("/api/users")
@Api(value = "用户管理", description = "用户增删改查相关接口")
public class UserController {

    /**
     * List all users with pagination
     */
    @GetMapping
    @ApiOperation(value = "获取用户列表", description = "分页查询用户列表")
    public Response<List<User>> listUsers(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword
    ) {
        // Mock data
        List<User> users = new ArrayList<>();
        User user = new User();
        user.setId(1001L);
        user.setUsername("john_doe");
        user.setEmail("john@example.com");
        user.setPhone("13800138000");
        user.setAge(25);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        users.add(user);

        return Response.success(users);
    }

    /**
     * Get user by ID
     */
    @GetMapping("/{id}")
    @ApiOperation(value = "获取用户详情", description = "根据用户ID获取用户详细信息")
    public Response<User> getUser(@PathVariable Long id) {
        // Mock data
        User user = new User();
        user.setId(id);
        user.setUsername("john_doe");
        user.setEmail("john@example.com");
        user.setPhone("13800138000");
        user.setAge(25);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        return Response.success(user);
    }

    /**
     * Create new user
     */
    @PostMapping
    @ApiOperation(value = "创建用户", description = "创建新用户账户")
    public Response<User> createUser(@Valid @RequestBody User user) {
        // Mock creation
        user.setId(System.currentTimeMillis());
        user.setStatus(UserStatus.ACTIVE);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        return Response.success("用户创建成功", user);
    }

    /**
     * Update user
     */
    @PutMapping("/{id}")
    @ApiOperation(value = "更新用户", description = "更新用户信息")
    public Response<User> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody User user
    ) {
        // Mock update
        user.setId(id);
        user.setUpdateTime(LocalDateTime.now());

        return Response.success("用户更新成功", user);
    }

    /**
     * Delete user
     */
    @DeleteMapping("/{id}")
    @ApiOperation(value = "删除用户", description = "根据ID删除用户")
    public Response<Void> deleteUser(@PathVariable Long id) {
        // Mock deletion
        return Response.success("用户删除成功", null);
    }

    /**
     * Update user status
     */
    @PatchMapping("/{id}/status")
    @ApiOperation(value = "更新用户状态", description = "更新用户的账户状态")
    public Response<User> updateUserStatus(
            @PathVariable Long id,
            @RequestParam UserStatus status
    ) {
        // Mock status update
        User user = new User();
        user.setId(id);
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());

        return Response.success("状态更新成功", user);
    }
}

