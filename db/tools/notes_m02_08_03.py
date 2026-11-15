"""M02-08-03 逐行说明（手写）。"""

NOTES_TEXT = {
    # WorksController
    '@GetMapping("/works/{id}")': "作品页路径：id 是作品（一级分类）",
    'public String detail(@PathVariable long id, ProductSearchCommand command, Model model) {': "路径 id + 查询参数（品类/排序/页码）",
    'command.setWorkId(String.valueOf(id));': "用路径 id 覆盖参数里的 workId，防止 ?workId=1 越权看别家",
    'model.addAttribute("work", catalogService.work(id));': "作品横幅数据；不存在或层级不对直接 404",
    'model.addAttribute("page", catalogService.search(command));': "商品列表复用同一个搜索服务",
    'return "work";': "视图名对应 templates/work.html",

    # work.html
    '<section class="work-hero">': "作品横幅：名称、code、品类数与在售数",
    'th:text="${#lists.size(work.categories())} + \' 个品类\'"': "品类数来自该作品的二级分类",
    '<a class="btn btn-ghost" th:href="@{/works}">← 全部作品</a>': "回作品墙，避免只能靠浏览器后退",
    'th:classappend="${query.categoryId == null or query.categoryId.isEmpty()} ? \'on\'"': "没选品类时「全部品类」高亮",
    'th:href="@{/works/{id}(id=${work.work().id}, categoryId=${c.id})}"': "点品类只筛该品类，作品 id 保留",
    "th:classappend=\"${query.categoryId == c.id.toString()} ? 'on'\"": "当前品类高亮（id 是字符串，需转换后比较）",
    "@{/works/{id}(id=${work.work().id}, categoryId=${query.categoryId}, sort='PRICE_ASC')}": "排序链接带上当前品类，切排序不丢筛选",
    '<p class="result-meta">共 <b th:text="${page.total}">0</b> 件</p>': "显示当前条件下的商品总数",
    '<p class="empty" th:if="${page.isEmpty()}">这个品类下暂时没有商品。</p>': "该品类无商品时的提示",
    "@{/works/{id}(id=${work.work().id}, categoryId=${query.categoryId}, sort=${query.sort}, page=${page.page + 1})}": "翻页保留品类与排序",

    # WorkPageTest
    'void workPageScopesProducts() throws Exception {': "用例：作品页只出现本作品商品",
    'long workA = seedWork("WA", "作品甲");': "造两个作品，各一件商品",
    '.andExpect(content().string(not(containsString("乙的单行本"))));': "别家商品不能串进来",
    'void categoryPillFilters() throws Exception {': "用例：品类胶囊能筛到只剩该品类",
    '.param("categoryId", String.valueOf(badge))': "只看徽章品类",
    'assertThat(badgePage).doesNotContain("丙的单行本");': "单行本应被筛掉",
    'void missingWorkReturns404() throws Exception {': "用例：不存在的作品 404",
    'void leafAsWorkReturns404() throws Exception {': "用例：二级分类不能当作品打开",
}