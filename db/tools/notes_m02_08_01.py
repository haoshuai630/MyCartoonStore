"""M02-08-01 逐行说明（手写）。"""

NOTES_TEXT = {
    # WorkSummary / WorkDetail
    'public record WorkSummary(long id, String code, String name, int categoryCount, int productCount) {':
        "作品墙卡片：作品 + 品类数 + 在售商品数，全部由一条 SQL 算出",
    'public record WorkDetail(Category work, List<Category> categories, int productCount) {':
        "作品页头部：作品本身、它的品类、在售商品数",

    # CategoryMapper
    'List<WorkSummary> selectWorkSummaries();': "作品墙取数：品类数与在售商品数用子查询一次算完",
    "SELECT p.id AS id, p.code AS code, p.name AS name,": "一级分类作为作品主体",
    "(SELECT COUNT(*) FROM category c WHERE c.parent_id = p.id) AS category_count,": "该作品下有几个品类",
    "WHERE c2.parent_id = p.id AND pr.status = 'ON_SALE') AS product_count": "只统计在售，下架商品不计入卡片数字",
    'WHERE p.parent_id IS NULL': "只要一级分类（作品）",
    'int countOnSaleByWork(long workId);': "作品页横幅上的在售数量",

    # CatalogService
    'public java.util.List<org.csu.mycartoonstore.catalog.dto.WorkSummary> works() {': "作品墙：直接返回 Mapper 的统计结果",
    'public org.csu.mycartoonstore.catalog.dto.WorkDetail work(long id) {': "作品页：只接受一级分类",
    'if (work == null || work.getParentId() != null) {': "二级分类或不存在的 id 都当作品不存在",
    'throw BizException.notFound("作品不存在");': "统一 404，不区分「没有」和「层级不对」",
    'query.setWorkId(parseCategoryId(command.getWorkId()));': "作品 id 与分类 id 用同一套正整数校验",

    # ProductQuery / Command
    '/** 作品（一级分类）id；指定时只查该作品下的全部品类 */': "作品页用它圈定范围，品类筛选再叠加 categoryId",
    'private Long workId;': "为空表示不限作品",

    # ProductMapper.xml
    '<if test="q.workId != null">': "作品页过滤：只查该作品下属品类的商品",
    'AND c.parent_id = #{q.workId}': "商品的分类 c 的父级是作品，正好一条条件搞定",
    "<when test=\"q.productType == 'MERCH'\">AND c.code NOT LIKE '%-COMIC'</when>": "周边 = 非单行本，新增模型/抱枕品类不用改 SQL",

    # 测试
    'void worksDelegatesToMapper() {': "用例：作品墙直接透传 Mapper 结果",
    'void workReturnsDetail() {': "用例：作品页返回作品、品类与在售数",
    'void workRejectsSecondaryCategory() {': "用例：二级分类不能当作品打开",
    'void workNotFound() {': "用例：不存在的作品 id 返回 404",
}