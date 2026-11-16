package org.csu.mycartoonstore.order.service; // 下单事务：写订单与明细、扣库存、删结算条目，同成同败。

import org.csu.mycartoonstore.cart.dto.CartLine; // 锁定的购物车行：条目、商品、数量、版本、单价。
import org.csu.mycartoonstore.cart.dto.CartSummary; // 复用购物车汇总做快照比对。
import org.csu.mycartoonstore.cart.service.CartService; // 锁车与删条目都走购物车模块。
import org.csu.mycartoonstore.catalog.entity.Product; // 扣库存前需要当前状态与库存。
import org.csu.mycartoonstore.catalog.mapper.ProductMapper; // 行锁 + 乐观扣库存。
import org.csu.mycartoonstore.common.exception.BizException; // 失败转 409/500 并回滚。
import org.csu.mycartoonstore.common.money.MoneyUtils; // 行金额统一两位小数。
import org.csu.mycartoonstore.order.dto.CheckoutSnapshot; // 提交时的确认凭据。
import org.csu.mycartoonstore.order.dto.PlacedOrder; // 返回值：订单 + 明细。
import org.csu.mycartoonstore.order.dto.Receiver; // 已校验的收货信息。
import org.csu.mycartoonstore.order.entity.SalesOrder; // 订单主表实体。
import org.csu.mycartoonstore.order.entity.SalesOrderItem; // 订单明细实体。
import org.csu.mycartoonstore.order.mapper.SalesOrderItemMapper; // 写明细。
import org.csu.mycartoonstore.order.mapper.SalesOrderMapper; // 写订单。
import org.springframework.stereotype.Service; // 业务服务。
import org.springframework.transaction.annotation.Transactional; // 事务边界。

import java.math.BigDecimal; // 金额合计。
import java.util.ArrayList; // 收集明细。
import java.util.List; // 行列表。
import java.util.Map; // id → 商品。
import java.util.function.Function; // 收集 Map。
import java.util.stream.Collectors; // 收集 Map。

// M05-03：依据 docs/01 FR-O-02、docs/02 §7.3、docs/06 AC-14～17。
// 负责一次事务内完成：校验快照 → 锁车 → 锁商品 → 写订单与明细 → 扣库存 → 删结算条目。
// 不负责支付、发货与页面渲染（M06 / M07 / M05-04）。
@Service // 业务服务：Controller 只调它。
public class OrderService {

    /** 下单初始状态：等待模拟支付。 */
    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";

    private final CartService cartService; // 锁车、删条目。
    private final CheckoutService checkoutService; // 快照比对与超时判断。
    private final ProductMapper productMapper; // 商品行锁与扣库存。
    private final SalesOrderMapper orderMapper; // 写订单。
    private final SalesOrderItemMapper itemMapper; // 写明细。
    private final OrderNoGenerator orderNoGenerator; // 生成唯一订单号。

    public OrderService(CartService cartService,
                        CheckoutService checkoutService,
                        ProductMapper productMapper,
                        SalesOrderMapper orderMapper,
                        SalesOrderItemMapper itemMapper,
                        OrderNoGenerator orderNoGenerator) { // 构造器注入六个协作方。
        this.cartService = cartService; // 保存购物车服务。
        this.checkoutService = checkoutService; // 保存结算服务。
        this.productMapper = productMapper; // 保存商品 Mapper。
        this.orderMapper = orderMapper; // 保存订单 Mapper。
        this.itemMapper = itemMapper; // 保存明细 Mapper。
        this.orderNoGenerator = orderNoGenerator; // 保存订单号生成器。
    }

    /**
     * 创建订单：任何一步失败，订单、明细、库存、购物车全部回滚。
     *
     * @param userId   当前用户
     * @param snapshot 确认页生成的快照
     * @param receiver 已校验的收货信息
     * @return 已写入的订单与明细
     */
    @Transactional // 整个方法一个事务：抛 RuntimeException 即回滚。
    public PlacedOrder placeOrder(long userId, CheckoutSnapshot snapshot, Receiver receiver) {
        // 没有快照、或快照不是本人的：不能下单。
        if (snapshot == null || snapshot.getUserId() != userId) {
            throw BizException.conflict("确认已失效，请重新确认");
        }
        // 确认超过 10 分钟：即使车没变也要求重新确认。
        if (checkoutService.expired(snapshot)) {
            throw BizException.conflict("确认已超时，请重新确认");
        }

        // 先锁本人购物车行：同一辆车的并发提交在这里排队。
        List<CartLine> lines = cartService.lockForCheckout(userId);
        if (lines.isEmpty()) {
            throw BizException.conflict("购物车为空，请重新选购");
        }
        // 与快照逐项比对：条目、数量、版本、价格、总额任一不同都拒绝。
        if (!checkoutService.matches(snapshot, CartSummary.of(lines))) {
            throw BizException.conflict("购物车或价格已变化，请重新确认");
        }

        // 商品按 id 升序锁定，避免两个事务锁顺序相反造成死锁。
        List<Long> productIds = lines.stream()
                .map(CartLine::getProductId)
                .distinct()
                .sorted()
                .toList();
        Map<Long, Product> products = productMapper.lockByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        // 逐条校验：商品仍在售且库存足够（快照比对只看价格，不保证库存）。
        for (CartLine line : lines) {
            Product product = products.get(line.getProductId());
            if (product == null || !"ON_SALE".equals(product.getStatus())) {
                throw BizException.conflict("商品「" + line.getProductName() + "」已下架，请重新确认");
            }
            if (product.getStock() == null || product.getStock() < line.getQuantity()) {
                throw BizException.conflict("商品「" + line.getProductName() + "」库存不足，请重新确认");
            }
        }

        // 总额只由服务端算：各行当前单价 × 数量再求和。
        BigDecimal total = lines.stream()
                .map(CartLine::getLineAmount)
                .reduce(BigDecimal.ZERO.setScale(MoneyUtils.SCALE), BigDecimal::add);

        // 写订单主表：状态、总额、收货快照都在这里落库。
        SalesOrder order = new SalesOrder();
        order.setOrderNo(orderNoGenerator.next()); // 服务端生成的唯一订单号。
        order.setUserId(userId); // 归属用户。
        order.setStatus(PENDING_PAYMENT); // 初始待支付。
        order.setTotalAmount(total); // 明细合计。
        order.setReceiverName(receiver.getName()); // 收货快照。
        order.setReceiverPhone(receiver.getPhone()); // 电话快照。
        order.setReceiverAddress(receiver.getAddress()); // 地址快照。
        orderMapper.insert(order); // 主表先插，拿到自增 id。

        // 写明细：名称、封面、SKU、单价全部取成交时的快照。
        List<SalesOrderItem> items = new ArrayList<>(lines.size());
        for (CartLine line : lines) {
            Product product = products.get(line.getProductId()); // 同一批锁定的商品。
            SalesOrderItem item = new SalesOrderItem();
            item.setOrderId(order.getId()); // 归属订单。
            item.setProductId(product.getId()); // 商品引用。
            item.setSkuSnapshot(product.getSku()); // SKU 快照。
            item.setProductNameSnapshot(product.getName()); // 名称快照。
            item.setCoverImageSnapshot(product.getCoverImage()); // 封面快照。
            item.setUnitPrice(product.getPrice()); // 成交单价。
            item.setQuantity(line.getQuantity()); // 数量。
            item.setLineAmount(MoneyUtils.multiply(product.getPrice(), line.getQuantity())); // 行金额。
            itemMapper.insert(item); // 逐条插入。
            items.add(item); // 收集返回值。
        }

        // 扣库存：影响 0 行说明被别人先买走，抛异常整体回滚。
        for (CartLine line : lines) {
            int rows = productMapper.deductStock(line.getProductId(), line.getQuantity());
            if (rows == 0) {
                throw BizException.conflict("商品「" + line.getProductName() + "」库存不足，请重新确认");
            }
        }

        // 删除本次结算的条目：行数必须与核对条数一致，否则说明车被并发改过。
        int deleted = cartService.removeCheckedItems(userId, lines.stream().map(CartLine::getItemId).toList());
        if (deleted != lines.size()) {
            throw BizException.conflict("购物车已变化，请重新确认");
        }
        return new PlacedOrder(order, items); // 事务提交后返回订单与明细。
    }
}
