"""常见 import 的作用说明：说清"这个包在本文件里干什么"。

键是 import 语句去掉分号后的完整文本，找不到就返回空（页面显示「—」）。
"""

IMPORT_NOTES = {
    "import jakarta.servlet.http.HttpServletResponse": "Servlet 的响应对象：这里只用它设置 400/404 状态码",
    "import java.math.BigDecimal": "金额用 BigDecimal，避免 double 的精度误差",
    "import org.csu.mycartoonstore.catalog.dto.ProductSearchCommand": "列表筛选参数对象，控制器直接接收查询参数",
    "import org.csu.mycartoonstore.catalog.service.CatalogService": "商品查询服务：分类树、分页搜索、详情",
    "import org.csu.mycartoonstore.common.exception.BizException": "业务异常，携带错误码与可直接展示的文案",
    "import org.csu.mycartoonstore.common.exception.ErrorCode": "错误码枚举，用来区分 400 与 404",
    "import org.csu.mycartoonstore.web.support.AbstractIntegrationTest": "测试基类：启动完整上下文并重建七张表",
    "import org.junit.jupiter.api.DisplayName": "JUnit 5 注解，给用例起可读名字",
    "import org.junit.jupiter.api.Test": "JUnit 5 注解，标记可执行用例",
    "import org.springframework.beans.factory.annotation.Autowired": "Spring 注解，按类型注入依赖",
    "import org.springframework.boot.test.context.SpringBootTest": "启动完整 Spring 上下文（含数据源）",
    "import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc": "自动配置 MockMvc，用于不起端口发请求",
    "import org.springframework.jdbc.core.JdbcTemplate": "Spring 的 JDBC 模板，直接执行 SQL 造数据",
    "import org.springframework.stereotype.Controller": "Spring MVC 注解，标记控制器",
    "import org.springframework.test.context.ActiveProfiles": "指定 test 配置，连测试库",
    "import org.springframework.test.web.servlet.MockMvc": "模拟 MVC 请求，直接调控制器并拿到渲染结果",
    "import org.springframework.ui.Model": "MVC 模型，控制器往里放数据给模板",
    "import org.springframework.web.bind.annotation.ControllerAdvice": "全局增强，集中处理异常",
    "import org.springframework.web.bind.annotation.ExceptionHandler": "指定处理哪类异常的方法",
    "import org.springframework.web.bind.annotation.GetMapping": "映射 GET 请求路径",
    "import static org.assertj.core.api.Assertions.assertThat": "AssertJ 断言入口，用流式写法断言结果",
    "import static org.hamcrest.Matchers.containsString": "Hamcrest 匹配器：断言字符串包含某段文本",
    "import static org.hamcrest.Matchers.not": "Hamcrest 匹配器：把断言取反",
    "import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get": "构造 GET 请求",
    "import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content": "断言响应体内容",
    "import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status": "断言 HTTP 状态码",
}