"""M02-08-02 逐行说明（手写）。"""

NOTES_TEXT = {
    # WorksController
    'private final CatalogService catalogService;': "作品墙数据来自查询服务",
    'public WorksController(CatalogService catalogService) {': "注入查询服务",
    '@GetMapping("/works")': "作品墙入口：导航里的「作品」指向这里",
    'public String list(Model model) {': "不加筛选参数，作品墙一次展示全部作品",
    'model.addAttribute("works", catalogService.works());': "一条 SQL 取回作品 + 品类数 + 在售数",
    'return "works";': "视图名对应 templates/works.html",

    # works.html
    '<h2>全部作品</h2>': "标题固定；右侧显示作品总数",
    "th:text=\"'共 ' + ${#lists.size(works)} + ' 部'\"": "作品总数，作品多了也不会失真",
    '<div class="works-grid" th:if="${!#lists.isEmpty(works)}">': "空库时不渲染网格，交给下面的空状态",
    'th:each="w : ${works}" th:href="@{/works/{id}(id=${w.id})}"': "每张卡点进该作品页",
    '<div class="work-cover">': "封面区：用该作品第一卷的封面",
    "th:src=\"${'/images/products/' + w.code + '-C01/cover.svg'}\"": "封面按作品 code 拼，-C01 是每部作品的第一卷",
    '<div class="work-body">': "卡片文字区：名称、code、品类数、在售数",
    '<span class="work-code" th:text="${w.code}">CODE</span>': "英文 code 便于与数据库核对",
    'th:text="${w.categoryCount()} + \' 个品类\'"': "该作品下有几个品类",
    'th:text="${w.productCount()} + \' 件在售\'"': "只统计在售，下架不计",
    '<p class="empty" th:if="${#lists.isEmpty(works)}">还没有任何作品。</p>': "空库时的可读提示",

    # WorksPageTest
    'class WorksPageTest extends AbstractIntegrationTest {': "测试类：每个用例前重建七张表",
    'void worksShowNameAndCounts() throws Exception {': "用例：作品墙显示名称与两个计数",
    'long workId = seedWork("W1", "测试作品甲");': "造一部作品",
    'long comic = seedCategory("W1-COMIC", workId, "单行本");': "给它挂两个品类",
    '.andExpect(content().string(containsString("2 个品类")))': "品类数按实际子分类算",
    '.andExpect(content().string(containsString("2 件在售")))': "在售数只算 ON_SALE",
    '.andExpect(content().string(containsString("共 1 部")))': "作品总数正确",
    'void worksEmptyState() throws Exception {': "用例：空库显示空状态",
    'private long seedWork(String code, String name) {': "插入一级分类并返回 id",
    'private long seedCategory(String code, long parentId, String name) {': "插入二级分类并返回 id",
}