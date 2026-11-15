"""M02-08-04~06 逐行说明（手写）。"""

NOTES_TEXT = {
    # category.html
    '<form class="filter-bar" th:action="@{/category}" method="get">': "筛选条用一个 GET 表单提交，作品多了也不会撑爆页面",
    '<select name="workId">': "作品下拉：选项来自 works()，选中值回填 query.workId",
    '<option value="">全部作品</option>': "空值表示不按作品过滤",
    'th:selected="${query.workId == w.id.toString()}"': "当前作品保持选中，翻页后不丢",
    '<input type="hidden" name="productType" th:value="${query.productType}">': "表单里带上类型，避免点「筛选」把类型清空",
    '<input type="hidden" name="sort" th:value="${query.sort}">': "排序同样保留",
    '<input name="keyword" th:value="${query.keyword}" placeholder="商品名称">': "关键字回填，用户能看到自己搜了什么",
    '<button class="btn btn-primary" type="submit">筛选</button>': "只有点按钮才提交，不依赖 JS",
    '<a class="btn btn-ghost" th:href="@{/category}">重置</a>': "一键回到无筛选状态",
    "@{/category(workId=${query.workId}, keyword=${query.keyword}, sort=${query.sort}, productType='MERCH')}": "类型链接保留作品与关键字，切类型不丢上下文",
    "@{/category(workId=${query.workId}, keyword=${query.keyword}, productType=${query.productType}, sort='PRICE_DESC')}": "排序链接保留作品、关键字与类型",
    'th:href="@{/category(workId=${query.workId}, keyword=${query.keyword}, productType=${query.productType}, sort=${query.sort}, page=${page.page + 1})}': "翻页保留全部筛选条件",

    # CategoryPageTest
    'void filterByWork() throws Exception {': "用例：作品下拉能筛到只剩该作品",
    'long workId = jdbc.queryForObject("SELECT id FROM category WHERE code = \'T1\'", Long.class);': "取作品 id 作为下拉值",
    '.param("workId", String.valueOf(workId))': "模拟选中该作品",
    '.andExpect(content().string(containsString("单行本1")))': "本作品商品出现",
    'insertProduct("X01", otherComic, "别家商品", "28.00", 10, "ON_SALE", 1);': "另造一部作品的一件商品，验证不会串进来",
    '.andExpect(content().string(not(containsString("周边M01"))));': "别家作品的商品不能出现",
}