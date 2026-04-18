package ai.csap.apidoc.devtools;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @author yangchengfu
 * @description Devtools 页面视图控制器
 * @date 2025-10-20
 */
@Controller
public class DevtoolsViewController {

    /**
     * 访问开发工具页面
     *
     * 访问路径：http://localhost:8085/devtools-ui
     *
     * 注意：使用 ** 通配符匹配所有子路径，让 React Router 能处理前端路由
     * 例如：
     * - /devtools-ui → 转发到 HTML
     * - /devtools-ui/api → 转发到 HTML（React Router 处理）
     * - /devtools-ui/login → 转发到 HTML（React Router 处理）
     *
     * @return 转发到静态资源路径
     */
    @GetMapping(value = {"/devtools-ui", "/devtools-ui/**"})
    public String devtoolsPage() {
        return "forward:/csap-api-devtools.html";
    }
}
