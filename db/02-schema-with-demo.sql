-- MyCartoonStore 实验一：表结构与演示数据（独立完整脚本）
-- 由 db/tools/gen_demo_sql.py 生成，请勿手工修改；改数据请改生成器后重跑。
-- 与 01-schema.sql 二选一，不按编号依次执行；此文件已包含完整表结构。
-- 在空的 MyCartoonStore 库中首次执行；不 DROP、不清空、不覆盖已有表。
-- 教学账号统一明文 Cartoon123!，BCrypt cost=10，每个账号独立盐。
-- 演示数据仅用于教学，商品、订单、金额均为虚构。

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET SESSION time_zone = '+08:00';

CREATE DATABASE IF NOT EXISTS MyCartoonStore
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE MyCartoonStore;

-- ===== 七张表的统一结构开始 =====

-- 1. 教学账号：登录身份与普通用户/管理员角色。
CREATE TABLE store_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部主键，Java Long',
    username VARCHAR(50) NOT NULL COMMENT '登录名，创建后不修改，不区分大小写唯一',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt散列，不保存明文密码',
    display_name VARCHAR(50) NOT NULL COMMENT '显示名称',
    role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CUSTOMER或ADMIN',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ENABLED',
    email VARCHAR(100) NULL,
    phone VARCHAR(30) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    CONSTRAINT ck_user_username CHECK (CHAR_LENGTH(TRIM(username)) > 0),
    CONSTRAINT ck_user_password CHECK (CHAR_LENGTH(TRIM(password_hash)) > 0),
    CONSTRAINT ck_user_display_name CHECK (CHAR_LENGTH(TRIM(display_name)) > 0),
    CONSTRAINT ck_user_role CHECK (role IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT ck_user_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户及管理员账号';

-- 2. 两级分类：parent_id为空表示一级；二级必须指向一级，由Service校验。
-- parent_key是生成列：插入/更新实体时不要写入它。
CREATE TABLE category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL COMMENT '全局唯一编码，创建后不修改',
    parent_id BIGINT NULL COMMENT '一级为空，二级指向一级，创建后不修改',
    parent_key BIGINT GENERATED ALWAYS AS (COALESCE(parent_id, 0)) STORED,
    name VARCHAR(50) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_code (code),
    UNIQUE KEY uk_category_sibling_name (parent_key, name),
    KEY idx_category_parent_sort (parent_id, sort_order, id),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_category_code CHECK (CHAR_LENGTH(TRIM(code)) > 0),
    CONSTRAINT ck_category_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_category_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固定两级商品分类';

-- 3. 一个商品对应一个可销售SKU，不做多规格；下架代替删除。
CREATE TABLE product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sku VARCHAR(40) NOT NULL COMMENT '唯一销售编码，创建后不修改',
    category_id BIGINT NOT NULL COMMENT '必须归属二级分类，由Service校验',
    name VARCHAR(120) NOT NULL,
    summary VARCHAR(500) NULL,
    description TEXT NULL COMMENT '纯文本详情，模板转义展示',
    cover_image VARCHAR(255) NOT NULL DEFAULT 'images/logo.png' COMMENT '站内静态资源相对路径',
    price DECIMAL(12,2) NOT NULL,
    stock INT NOT NULL DEFAULT 0 COMMENT '可销售库存：下单扣减，未支付取消恢复',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    sort_order INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0 COMMENT '所有商品写操作显式递增，防旧值覆盖',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_sku (sku),
    KEY idx_product_category_status_sort (category_id, status, sort_order, id),
    KEY idx_product_status_sort (status, sort_order, id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_product_sku CHECK (CHAR_LENGTH(TRIM(sku)) > 0),
    CONSTRAINT ck_product_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_product_cover CHECK (CHAR_LENGTH(TRIM(cover_image)) > 0),
    CONSTRAINT ck_product_price CHECK (price > 0),
    CONSTRAINT ck_product_stock CHECK (stock >= 0),
    CONSTRAINT ck_product_status CHECK (status IN ('DRAFT', 'ON_SALE')),
    CONSTRAINT ck_product_sort CHECK (sort_order >= 0),
    CONSTRAINT ck_product_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品及可销售库存';

-- 4. 图片相册与封面分开；多个商品可复用相同的教学图片。
CREATE TABLE product_image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    image_path VARCHAR(255) NOT NULL,
    alt_text VARCHAR(120) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_image_product_sort (product_id, sort_order, id),
    CONSTRAINT fk_image_product FOREIGN KEY (product_id) REFERENCES product (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_image_path CHECK (CHAR_LENGTH(TRIM(image_path)) > 0),
    CONSTRAINT ck_image_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品详情相册';

-- 5. 登录用户的持久购物车：价格不存这里；购物车不占库存。
CREATE TABLE cart_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_user_product (user_id, product_id),
    KEY idx_cart_product (product_id),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES store_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES product (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_cart_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT ck_cart_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按用户持久化的购物车条目';

-- 6. 订单主表：模拟付款成功即PAID（待发货），没有真实支付渠道或退款。
CREATE TABLE sales_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL COMMENT '服务端生成的唯一展示订单号',
    user_id BIGINT NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING_PAYMENT',
    total_amount DECIMAL(12,2) NOT NULL,
    receiver_name VARCHAR(50) NOT NULL,
    receiver_phone VARCHAR(30) NOT NULL,
    receiver_address VARCHAR(255) NOT NULL COMMENT '下单时完整收货地址快照',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    payment_method VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '成功后固定MOCK',
    payment_no VARCHAR(40) NULL COMMENT '模拟成功凭证，未支付为空，可空唯一',
    paid_at DATETIME NULL,
    cancelled_at DATETIME NULL,
    shipped_at DATETIME NULL,
    completed_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_order_payment_no (payment_no),
    KEY idx_order_user_created (user_id, created_at, id),
    KEY idx_order_status_created (status, created_at, id),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES store_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_order_no CHECK (CHAR_LENGTH(TRIM(order_no)) > 0),
    CONSTRAINT ck_order_total CHECK (total_amount > 0),
    CONSTRAINT ck_order_receiver CHECK (
        CHAR_LENGTH(TRIM(receiver_name)) > 0 AND
        CHAR_LENGTH(TRIM(receiver_phone)) > 0 AND
        CHAR_LENGTH(TRIM(receiver_address)) > 0
    ),
    CONSTRAINT ck_order_status CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_order_payment_info CHECK (
        (status IN ('PENDING_PAYMENT', 'CANCELLED')
            AND payment_method IS NULL AND payment_no IS NULL AND paid_at IS NULL)
        OR
        (status IN ('PAID', 'SHIPPED', 'COMPLETED')
            AND payment_method IS NOT NULL AND payment_method = 'MOCK'
            AND payment_no IS NOT NULL AND CHAR_LENGTH(TRIM(payment_no)) > 0
            AND paid_at IS NOT NULL)
    ),
    CONSTRAINT ck_order_cancel_time CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
        OR (status <> 'CANCELLED' AND cancelled_at IS NULL)
    ),
    CONSTRAINT ck_order_ship_time CHECK (
        (status IN ('SHIPPED', 'COMPLETED') AND shipped_at IS NOT NULL)
        OR (status NOT IN ('SHIPPED', 'COMPLETED') AND shipped_at IS NULL)
    ),
    CONSTRAINT ck_order_complete_time CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT ck_order_time_sequence CHECK (
        (paid_at IS NULL OR paid_at >= created_at) AND
        (cancelled_at IS NULL OR cancelled_at >= created_at) AND
        (shipped_at IS NULL OR shipped_at >= paid_at) AND
        (completed_at IS NULL OR completed_at >= shipped_at)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单及收货、模拟支付信息';

-- 7. 订单明细：保留成交时快照，不随商品后续变化而更新。
CREATE TABLE sales_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku_snapshot VARCHAR(40) NOT NULL,
    product_name_snapshot VARCHAR(120) NOT NULL,
    cover_image_snapshot VARCHAR(255) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    line_amount DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_item_product (order_id, product_id),
    KEY idx_order_item_product (product_id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES sales_order (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_order_item_sku CHECK (CHAR_LENGTH(TRIM(sku_snapshot)) > 0),
    CONSTRAINT ck_order_item_name CHECK (CHAR_LENGTH(TRIM(product_name_snapshot)) > 0),
    CONSTRAINT ck_order_item_cover CHECK (CHAR_LENGTH(TRIM(cover_image_snapshot)) > 0),
    CONSTRAINT ck_order_item_price CHECK (unit_price > 0),
    CONSTRAINT ck_order_item_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT ck_order_item_amount CHECK (line_amount > 0 AND line_amount = unit_price * quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单商品快照与金额明细';

-- ===== 七张表的统一结构结束 =====

-- Service仍需保证：只允许二级分类归属商品、禁止三级/循环分类、角色与所有权、
-- 购物车最多20种、用户行锁、库存和version更新、状态迁移、订单合计等于明细合计。
-- 下单的订单/明细/扣库存/删除结算条目须同一事务，不由触发器自动完成。

-- ===== 演示数据开始 =====

-- 1. 教学账号：4 个，其中 carol 为禁用样例
INSERT INTO store_user (username, password_hash, display_name, role, status, email) VALUES
('alice', '$2a$10$j/75MLefRUs.J6CnH/rosemt5nzlyPmP8ZqxiTvOHWGQx6yIhAB96', '爱丽丝', 'CUSTOMER', 'ENABLED', 'alice@example.com'),
('bob', '$2a$10$FLGRU.yNTnPKj7C2Sh0.c.p0//xTFpIiXw2j8AwQMTOLgB.HBTLbW', '鲍勃', 'CUSTOMER', 'ENABLED', 'bob@example.com'),
('carol', '$2a$10$55myfBkMkG8w08nnWw4.y.glTUMWdfUNJFBdNFDuLVJK9uM5GWQUK', '卡萝', 'CUSTOMER', 'DISABLED', 'carol@example.com'),
('admin', '$2a$10$hJrmVNne3wWlQhSr/ndtyemGq9r00m4n2R6UhRk1eIews6E4Gr0u2', '店长', 'ADMIN', 'ENABLED', 'admin@example.com');

-- 2. 分类：5 个一级（作品）+ 10 个二级（单行本 / 官方周边）
INSERT INTO category (id, code, parent_id, name, sort_order) VALUES
(1, 'STARLINE', NULL, '星轨列车', 1),
(2, 'MISTPORT', NULL, '雾港侦探', 2),
(3, 'ONIGIRI', NULL, '饭团小馆', 3),
(4, 'IRONWOOD', NULL, '机械之森', 4),
(5, 'CICADA', NULL, '夏夜蝉鸣', 5);

INSERT INTO category (id, code, parent_id, name, sort_order) VALUES
(100, 'STARLINE-COMIC', 1, '单行本', 1),
(101, 'STARLINE-MERCH', 1, '官方周边', 2),
(102, 'MISTPORT-COMIC', 2, '单行本', 1),
(103, 'MISTPORT-MERCH', 2, '官方周边', 2),
(104, 'ONIGIRI-COMIC', 3, '单行本', 1),
(105, 'ONIGIRI-MERCH', 3, '官方周边', 2),
(106, 'IRONWOOD-COMIC', 4, '单行本', 1),
(107, 'IRONWOOD-MERCH', 4, '官方周边', 2),
(108, 'CICADA-COMIC', 5, '单行本', 1),
(109, 'CICADA-MERCH', 5, '官方周边', 2);

-- 3. 商品：60 件 = 5 部作品 ×（6 卷单行本 + 6 件周边）
--    54 上架 / 6 下架；其中 3 件上架售罄、2 件库存为 1（边界样例）
INSERT INTO product (sku, category_id, name, summary, description, cover_image, price, stock, status, sort_order, created_at) VALUES
('STARLINE-C01', 100, '《星轨列车》第1卷', '故事从一列永不到站的列车开始。', '《星轨列车》第1卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/STARLINE-C01/cover.svg', 28.00, 12, 'ON_SALE', 1, DATE_SUB(NOW(), INTERVAL 6 DAY)),
('STARLINE-C02', 100, '《星轨列车》第2卷', '车窗外是不断被重写的星空。', '《星轨列车》第2卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/STARLINE-C02/cover.svg', 28.00, 1, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 7 DAY)),
('STARLINE-C03', 100, '《星轨列车》第3卷', '乘客名单上出现了自己的名字。', '《星轨列车》第3卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/STARLINE-C03/cover.svg', 30.00, 14, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 8 DAY)),
('STARLINE-C04', 100, '《星轨列车》第4卷', '终点站原来根本没有站台。', '《星轨列车》第4卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/STARLINE-C04/cover.svg', 30.00, 15, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 9 DAY)),
('STARLINE-C05', 100, '《星轨列车》第5卷', '有人在中途下了车，再也没回来。', '《星轨列车》第5卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/STARLINE-C05/cover.svg', 32.00, 16, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 10 DAY)),
('STARLINE-C06', 100, '《星轨列车》第6卷', '最后一卷，列车驶入黎明。', '《星轨列车》第6卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/STARLINE-C06/cover.svg', 34.00, 0, 'DRAFT', 100, DATE_SUB(NOW(), INTERVAL 11 DAY)),
('STARLINE-M01', 101, '星轨列车 列车长亚克力立牌', '官方授权周边，随书附赠收藏卡。', '星轨列车「列车长亚克力立牌」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/STARLINE-M01/cover.svg', 89.00, 0, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 8 DAY)),
('STARLINE-M02', 101, '星轨列车 车票金属徽章', '官方授权周边，随书附赠收藏卡。', '星轨列车「车票金属徽章」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/STARLINE-M02/cover.svg', 39.00, 8, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 9 DAY)),
('STARLINE-M03', 101, '星轨列车 星空帆布袋', '官方授权周边，随书附赠收藏卡。', '星轨列车「星空帆布袋」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/STARLINE-M03/cover.svg', 69.00, 9, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 10 DAY)),
('STARLINE-M04', 101, '星轨列车 线路图线圈本', '官方授权周边，随书附赠收藏卡。', '星轨列车「线路图线圈本」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/STARLINE-M04/cover.svg', 29.00, 10, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 11 DAY)),
('STARLINE-M05', 101, '星轨列车 列车钥匙扣', '官方授权周边，随书附赠收藏卡。', '星轨列车「列车钥匙扣」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/STARLINE-M05/cover.svg', 45.00, 11, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 12 DAY)),
('STARLINE-M06', 101, '星轨列车 全彩设定画集', '官方授权周边，随书附赠收藏卡。', '星轨列车「全彩设定画集」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/STARLINE-M06/cover.svg', 128.00, 6, 'DRAFT', 100, DATE_SUB(NOW(), INTERVAL 13 DAY)),
('MISTPORT-C01', 102, '《雾港侦探》第1卷', '故事从一列永不到站的列车开始。', '《雾港侦探》第1卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/MISTPORT-C01/cover.svg', 28.00, 12, 'ON_SALE', 2, DATE_SUB(NOW(), INTERVAL 12 DAY)),
('MISTPORT-C02', 102, '《雾港侦探》第2卷', '车窗外是不断被重写的星空。', '《雾港侦探》第2卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/MISTPORT-C02/cover.svg', 28.00, 13, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 13 DAY)),
('MISTPORT-C03', 102, '《雾港侦探》第3卷', '乘客名单上出现了自己的名字。', '《雾港侦探》第3卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/MISTPORT-C03/cover.svg', 30.00, 0, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 14 DAY)),
('MISTPORT-C04', 102, '《雾港侦探》第4卷', '终点站原来根本没有站台。', '《雾港侦探》第4卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/MISTPORT-C04/cover.svg', 30.00, 15, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 15 DAY)),
('MISTPORT-C05', 102, '《雾港侦探》第5卷', '有人在中途下了车，再也没回来。', '《雾港侦探》第5卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/MISTPORT-C05/cover.svg', 32.00, 16, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 16 DAY)),
('MISTPORT-C06', 102, '《雾港侦探》第6卷', '最后一卷，列车驶入黎明。', '《雾港侦探》第6卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/MISTPORT-C06/cover.svg', 34.00, 0, 'DRAFT', 100, DATE_SUB(NOW(), INTERVAL 17 DAY)),
('MISTPORT-M01', 103, '雾港侦探 侦探风衣立牌', '官方授权周边，随书附赠收藏卡。', '雾港侦探「侦探风衣立牌」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/MISTPORT-M01/cover.svg', 89.00, 3, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 14 DAY)),
('MISTPORT-M02', 103, '雾港侦探 雾港地图海报', '官方授权周边，随书附赠收藏卡。', '雾港侦探「雾港地图海报」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/MISTPORT-M02/cover.svg', 49.00, 9, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 15 DAY)),
('MISTPORT-M03', 103, '雾港侦探 怀表造型挂件', '官方授权周边，随书附赠收藏卡。', '雾港侦探「怀表造型挂件」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/MISTPORT-M03/cover.svg', 59.00, 10, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 16 DAY)),
('MISTPORT-M04', 103, '雾港侦探 案件档案文件夹', '官方授权周边，随书附赠收藏卡。', '雾港侦探「案件档案文件夹」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/MISTPORT-M04/cover.svg', 25.00, 11, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 17 DAY)),
('MISTPORT-M05', 103, '雾港侦探 主角徽章套装', '官方授权周边，随书附赠收藏卡。', '雾港侦探「主角徽章套装」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/MISTPORT-M05/cover.svg', 45.00, 12, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 2 DAY)),
('MISTPORT-M06', 103, '雾港侦探 雾港设定集', '官方授权周边，随书附赠收藏卡。', '雾港侦探「雾港设定集」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/MISTPORT-M06/cover.svg', 118.00, 7, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 19 DAY)),
('ONIGIRI-C01', 104, '《饭团小馆》第1卷', '故事从一列永不到站的列车开始。', '《饭团小馆》第1卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/ONIGIRI-C01/cover.svg', 28.00, 12, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 18 DAY)),
('ONIGIRI-C02', 104, '《饭团小馆》第2卷', '车窗外是不断被重写的星空。', '《饭团小馆》第2卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/ONIGIRI-C02/cover.svg', 28.00, 13, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 19 DAY)),
('ONIGIRI-C03', 104, '《饭团小馆》第3卷', '乘客名单上出现了自己的名字。', '《饭团小馆》第3卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/ONIGIRI-C03/cover.svg', 30.00, 14, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 20 DAY)),
('ONIGIRI-C04', 104, '《饭团小馆》第4卷', '终点站原来根本没有站台。', '《饭团小馆》第4卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/ONIGIRI-C04/cover.svg', 30.00, 15, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 1 DAY)),
('ONIGIRI-C05', 104, '《饭团小馆》第5卷', '有人在中途下了车，再也没回来。', '《饭团小馆》第5卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/ONIGIRI-C05/cover.svg', 32.00, 16, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 22 DAY)),
('ONIGIRI-C06', 104, '《饭团小馆》第6卷', '最后一卷，列车驶入黎明。', '《饭团小馆》第6卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/ONIGIRI-C06/cover.svg', 34.00, 0, 'DRAFT', 100, DATE_SUB(NOW(), INTERVAL 23 DAY)),
('ONIGIRI-M01', 105, '饭团小馆 饭团造型抱枕', '官方授权周边，随书附赠收藏卡。', '饭团小馆「饭团造型抱枕」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/ONIGIRI-M01/cover.svg', 99.00, 1, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 20 DAY)),
('ONIGIRI-M02', 105, '饭团小馆 小馆围裙', '官方授权周边，随书附赠收藏卡。', '饭团小馆「小馆围裙」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/ONIGIRI-M02/cover.svg', 79.00, 8, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 21 DAY)),
('ONIGIRI-M03', 105, '饭团小馆 菜单贴纸包', '官方授权周边，随书附赠收藏卡。', '饭团小馆「菜单贴纸包」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/ONIGIRI-M03/cover.svg', 19.00, 9, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 22 DAY)),
('ONIGIRI-M04', 105, '饭团小馆 陶瓷马克杯', '官方授权周边，随书附赠收藏卡。', '饭团小馆「陶瓷马克杯」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/ONIGIRI-M04/cover.svg', 59.00, 10, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 23 DAY)),
('ONIGIRI-M05', 105, '饭团小馆 店长亚克力立牌', '官方授权周边，随书附赠收藏卡。', '饭团小馆「店长亚克力立牌」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/ONIGIRI-M05/cover.svg', 69.00, 11, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 24 DAY)),
('ONIGIRI-M06', 105, '饭团小馆 料理手账本', '官方授权周边，随书附赠收藏卡。', '饭团小馆「料理手账本」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/ONIGIRI-M06/cover.svg', 35.00, 6, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 25 DAY)),
('IRONWOOD-C01', 106, '《机械之森》第1卷', '故事从一列永不到站的列车开始。', '《机械之森》第1卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/IRONWOOD-C01/cover.svg', 28.00, 12, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 24 DAY)),
('IRONWOOD-C02', 106, '《机械之森》第2卷', '车窗外是不断被重写的星空。', '《机械之森》第2卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/IRONWOOD-C02/cover.svg', 28.00, 13, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 25 DAY)),
('IRONWOOD-C03', 106, '《机械之森》第3卷', '乘客名单上出现了自己的名字。', '《机械之森》第3卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/IRONWOOD-C03/cover.svg', 30.00, 14, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 26 DAY)),
('IRONWOOD-C04', 106, '《机械之森》第4卷', '终点站原来根本没有站台。', '《机械之森》第4卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/IRONWOOD-C04/cover.svg', 30.00, 15, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 27 DAY)),
('IRONWOOD-C05', 106, '《机械之森》第5卷', '有人在中途下了车，再也没回来。', '《机械之森》第5卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/IRONWOOD-C05/cover.svg', 32.00, 16, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 28 DAY)),
('IRONWOOD-C06', 106, '《机械之森》第6卷', '最后一卷，列车驶入黎明。', '《机械之森》第6卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/IRONWOOD-C06/cover.svg', 34.00, 0, 'DRAFT', 100, DATE_SUB(NOW(), INTERVAL 29 DAY)),
('IRONWOOD-M01', 107, '机械之森 机械鹿拼装模型', '官方授权周边，随书附赠收藏卡。', '机械之森「机械鹿拼装模型」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/IRONWOOD-M01/cover.svg', 199.00, 5, 'ON_SALE', 3, DATE_SUB(NOW(), INTERVAL 26 DAY)),
('IRONWOOD-M02', 107, '机械之森 齿轮金属徽章', '官方授权周边，随书附赠收藏卡。', '机械之森「齿轮金属徽章」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/IRONWOOD-M02/cover.svg', 45.00, 8, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 27 DAY)),
('IRONWOOD-M03', 107, '机械之森 森林夜光海报', '官方授权周边，随书附赠收藏卡。', '机械之森「森林夜光海报」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/IRONWOOD-M03/cover.svg', 55.00, 9, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 28 DAY)),
('IRONWOOD-M04', 107, '机械之森 工装短T', '官方授权周边，随书附赠收藏卡。', '机械之森「工装短T」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/IRONWOOD-M04/cover.svg', 89.00, 10, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 29 DAY)),
('IRONWOOD-M05', 107, '机械之森 机械书签套装', '官方授权周边，随书附赠收藏卡。', '机械之森「机械书签套装」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/IRONWOOD-M05/cover.svg', 29.00, 11, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 30 DAY)),
('IRONWOOD-M06', 107, '机械之森 机械设定画集', '官方授权周边，随书附赠收藏卡。', '机械之森「机械设定画集」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/IRONWOOD-M06/cover.svg', 138.00, 6, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 31 DAY)),
('CICADA-C01', 108, '《夏夜蝉鸣》第1卷', '故事从一列永不到站的列车开始。', '《夏夜蝉鸣》第1卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/CICADA-C01/cover.svg', 28.00, 12, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 30 DAY)),
('CICADA-C02', 108, '《夏夜蝉鸣》第2卷', '车窗外是不断被重写的星空。', '《夏夜蝉鸣》第2卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/CICADA-C02/cover.svg', 28.00, 13, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 31 DAY)),
('CICADA-C03', 108, '《夏夜蝉鸣》第3卷', '乘客名单上出现了自己的名字。', '《夏夜蝉鸣》第3卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/CICADA-C03/cover.svg', 30.00, 14, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 32 DAY)),
('CICADA-C04', 108, '《夏夜蝉鸣》第4卷', '终点站原来根本没有站台。', '《夏夜蝉鸣》第4卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/CICADA-C04/cover.svg', 30.00, 15, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 33 DAY)),
('CICADA-C05', 108, '《夏夜蝉鸣》第5卷', '有人在中途下了车，再也没回来。', '《夏夜蝉鸣》第5卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/CICADA-C05/cover.svg', 32.00, 16, 'ON_SALE', 4, DATE_SUB(NOW(), INTERVAL 34 DAY)),
('CICADA-C06', 108, '《夏夜蝉鸣》第6卷', '最后一卷，列车驶入黎明。', '《夏夜蝉鸣》第6卷正文，官方单行本。教学演示数据，非真实出版物。', 'images/products/CICADA-C06/cover.svg', 34.00, 0, 'DRAFT', 100, DATE_SUB(NOW(), INTERVAL 35 DAY)),
('CICADA-M01', 109, '夏夜蝉鸣 蝉鸣主题T恤', '官方授权周边，随书附赠收藏卡。', '夏夜蝉鸣「蝉鸣主题T恤」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/CICADA-M01/cover.svg', 89.00, 4, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 32 DAY)),
('CICADA-M02', 109, '夏夜蝉鸣 星空风铃', '官方授权周边，随书附赠收藏卡。', '夏夜蝉鸣「星空风铃」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/CICADA-M02/cover.svg', 69.00, 0, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 33 DAY)),
('CICADA-M03', 109, '夏夜蝉鸣 蝉翼亚克力挂件', '官方授权周边，随书附赠收藏卡。', '夏夜蝉鸣「蝉翼亚克力挂件」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/CICADA-M03/cover.svg', 39.00, 9, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 34 DAY)),
('CICADA-M04', 109, '夏夜蝉鸣 夏日明信片组', '官方授权周边，随书附赠收藏卡。', '夏夜蝉鸣「夏日明信片组」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/CICADA-M04/cover.svg', 25.00, 10, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 0 DAY)),
('CICADA-M05', 109, '夏夜蝉鸣 主角亚克力立牌', '官方授权周边，随书附赠收藏卡。', '夏夜蝉鸣「主角亚克力立牌」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/CICADA-M05/cover.svg', 79.00, 11, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 36 DAY)),
('CICADA-M06', 109, '夏夜蝉鸣 夏日画集', '官方授权周边，随书附赠收藏卡。', '夏夜蝉鸣「夏日画集」，官方授权周边。材质与规格以实物为准，教学演示数据。', 'images/products/CICADA-M06/cover.svg', 108.00, 6, 'ON_SALE', 100, DATE_SUB(NOW(), INTERVAL 37 DAY));

-- 4. 相册：100 条 = 20 件×3 张 + 20 件×2 张；另有 20 件无相册（用于回退封面）
INSERT INTO product_image (product_id, image_path, alt_text, sort_order) VALUES
((SELECT id FROM product WHERE sku='STARLINE-C01'), 'images/products/STARLINE-C01/1.svg', '《星轨列车》第1卷 图1', 1),
((SELECT id FROM product WHERE sku='STARLINE-C01'), 'images/products/STARLINE-C01/2.svg', '《星轨列车》第1卷 图2', 2),
((SELECT id FROM product WHERE sku='STARLINE-C01'), 'images/products/STARLINE-C01/3.svg', '《星轨列车》第1卷 图3', 3),
((SELECT id FROM product WHERE sku='STARLINE-C02'), 'images/products/STARLINE-C02/1.svg', '《星轨列车》第2卷 图1', 1),
((SELECT id FROM product WHERE sku='STARLINE-C02'), 'images/products/STARLINE-C02/2.svg', '《星轨列车》第2卷 图2', 2),
((SELECT id FROM product WHERE sku='STARLINE-C02'), 'images/products/STARLINE-C02/3.svg', '《星轨列车》第2卷 图3', 3),
((SELECT id FROM product WHERE sku='STARLINE-C03'), 'images/products/STARLINE-C03/1.svg', '《星轨列车》第3卷 图1', 1),
((SELECT id FROM product WHERE sku='STARLINE-C03'), 'images/products/STARLINE-C03/2.svg', '《星轨列车》第3卷 图2', 2),
((SELECT id FROM product WHERE sku='STARLINE-C03'), 'images/products/STARLINE-C03/3.svg', '《星轨列车》第3卷 图3', 3),
((SELECT id FROM product WHERE sku='STARLINE-C04'), 'images/products/STARLINE-C04/1.svg', '《星轨列车》第4卷 图1', 1),
((SELECT id FROM product WHERE sku='STARLINE-C04'), 'images/products/STARLINE-C04/2.svg', '《星轨列车》第4卷 图2', 2),
((SELECT id FROM product WHERE sku='STARLINE-C04'), 'images/products/STARLINE-C04/3.svg', '《星轨列车》第4卷 图3', 3),
((SELECT id FROM product WHERE sku='STARLINE-C05'), 'images/products/STARLINE-C05/1.svg', '《星轨列车》第5卷 图1', 1),
((SELECT id FROM product WHERE sku='STARLINE-C05'), 'images/products/STARLINE-C05/2.svg', '《星轨列车》第5卷 图2', 2),
((SELECT id FROM product WHERE sku='STARLINE-C06'), 'images/products/STARLINE-C06/1.svg', '《星轨列车》第6卷 图1', 1),
((SELECT id FROM product WHERE sku='STARLINE-C06'), 'images/products/STARLINE-C06/2.svg', '《星轨列车》第6卷 图2', 2),
((SELECT id FROM product WHERE sku='STARLINE-M01'), 'images/products/STARLINE-M01/1.svg', '星轨列车 列车长亚克力立牌 图1', 1),
((SELECT id FROM product WHERE sku='STARLINE-M01'), 'images/products/STARLINE-M01/2.svg', '星轨列车 列车长亚克力立牌 图2', 2),
((SELECT id FROM product WHERE sku='STARLINE-M02'), 'images/products/STARLINE-M02/1.svg', '星轨列车 车票金属徽章 图1', 1),
((SELECT id FROM product WHERE sku='STARLINE-M02'), 'images/products/STARLINE-M02/2.svg', '星轨列车 车票金属徽章 图2', 2),
((SELECT id FROM product WHERE sku='MISTPORT-C01'), 'images/products/MISTPORT-C01/1.svg', '《雾港侦探》第1卷 图1', 1),
((SELECT id FROM product WHERE sku='MISTPORT-C01'), 'images/products/MISTPORT-C01/2.svg', '《雾港侦探》第1卷 图2', 2),
((SELECT id FROM product WHERE sku='MISTPORT-C01'), 'images/products/MISTPORT-C01/3.svg', '《雾港侦探》第1卷 图3', 3),
((SELECT id FROM product WHERE sku='MISTPORT-C02'), 'images/products/MISTPORT-C02/1.svg', '《雾港侦探》第2卷 图1', 1),
((SELECT id FROM product WHERE sku='MISTPORT-C02'), 'images/products/MISTPORT-C02/2.svg', '《雾港侦探》第2卷 图2', 2),
((SELECT id FROM product WHERE sku='MISTPORT-C02'), 'images/products/MISTPORT-C02/3.svg', '《雾港侦探》第2卷 图3', 3),
((SELECT id FROM product WHERE sku='MISTPORT-C03'), 'images/products/MISTPORT-C03/1.svg', '《雾港侦探》第3卷 图1', 1),
((SELECT id FROM product WHERE sku='MISTPORT-C03'), 'images/products/MISTPORT-C03/2.svg', '《雾港侦探》第3卷 图2', 2),
((SELECT id FROM product WHERE sku='MISTPORT-C03'), 'images/products/MISTPORT-C03/3.svg', '《雾港侦探》第3卷 图3', 3),
((SELECT id FROM product WHERE sku='MISTPORT-C04'), 'images/products/MISTPORT-C04/1.svg', '《雾港侦探》第4卷 图1', 1),
((SELECT id FROM product WHERE sku='MISTPORT-C04'), 'images/products/MISTPORT-C04/2.svg', '《雾港侦探》第4卷 图2', 2),
((SELECT id FROM product WHERE sku='MISTPORT-C04'), 'images/products/MISTPORT-C04/3.svg', '《雾港侦探》第4卷 图3', 3),
((SELECT id FROM product WHERE sku='MISTPORT-C05'), 'images/products/MISTPORT-C05/1.svg', '《雾港侦探》第5卷 图1', 1),
((SELECT id FROM product WHERE sku='MISTPORT-C05'), 'images/products/MISTPORT-C05/2.svg', '《雾港侦探》第5卷 图2', 2),
((SELECT id FROM product WHERE sku='MISTPORT-C06'), 'images/products/MISTPORT-C06/1.svg', '《雾港侦探》第6卷 图1', 1),
((SELECT id FROM product WHERE sku='MISTPORT-C06'), 'images/products/MISTPORT-C06/2.svg', '《雾港侦探》第6卷 图2', 2),
((SELECT id FROM product WHERE sku='MISTPORT-M01'), 'images/products/MISTPORT-M01/1.svg', '雾港侦探 侦探风衣立牌 图1', 1),
((SELECT id FROM product WHERE sku='MISTPORT-M01'), 'images/products/MISTPORT-M01/2.svg', '雾港侦探 侦探风衣立牌 图2', 2),
((SELECT id FROM product WHERE sku='MISTPORT-M02'), 'images/products/MISTPORT-M02/1.svg', '雾港侦探 雾港地图海报 图1', 1),
((SELECT id FROM product WHERE sku='MISTPORT-M02'), 'images/products/MISTPORT-M02/2.svg', '雾港侦探 雾港地图海报 图2', 2),
((SELECT id FROM product WHERE sku='ONIGIRI-C01'), 'images/products/ONIGIRI-C01/1.svg', '《饭团小馆》第1卷 图1', 1),
((SELECT id FROM product WHERE sku='ONIGIRI-C01'), 'images/products/ONIGIRI-C01/2.svg', '《饭团小馆》第1卷 图2', 2),
((SELECT id FROM product WHERE sku='ONIGIRI-C01'), 'images/products/ONIGIRI-C01/3.svg', '《饭团小馆》第1卷 图3', 3),
((SELECT id FROM product WHERE sku='ONIGIRI-C02'), 'images/products/ONIGIRI-C02/1.svg', '《饭团小馆》第2卷 图1', 1),
((SELECT id FROM product WHERE sku='ONIGIRI-C02'), 'images/products/ONIGIRI-C02/2.svg', '《饭团小馆》第2卷 图2', 2),
((SELECT id FROM product WHERE sku='ONIGIRI-C02'), 'images/products/ONIGIRI-C02/3.svg', '《饭团小馆》第2卷 图3', 3),
((SELECT id FROM product WHERE sku='ONIGIRI-C03'), 'images/products/ONIGIRI-C03/1.svg', '《饭团小馆》第3卷 图1', 1),
((SELECT id FROM product WHERE sku='ONIGIRI-C03'), 'images/products/ONIGIRI-C03/2.svg', '《饭团小馆》第3卷 图2', 2),
((SELECT id FROM product WHERE sku='ONIGIRI-C03'), 'images/products/ONIGIRI-C03/3.svg', '《饭团小馆》第3卷 图3', 3),
((SELECT id FROM product WHERE sku='ONIGIRI-C04'), 'images/products/ONIGIRI-C04/1.svg', '《饭团小馆》第4卷 图1', 1),
((SELECT id FROM product WHERE sku='ONIGIRI-C04'), 'images/products/ONIGIRI-C04/2.svg', '《饭团小馆》第4卷 图2', 2),
((SELECT id FROM product WHERE sku='ONIGIRI-C04'), 'images/products/ONIGIRI-C04/3.svg', '《饭团小馆》第4卷 图3', 3),
((SELECT id FROM product WHERE sku='ONIGIRI-C05'), 'images/products/ONIGIRI-C05/1.svg', '《饭团小馆》第5卷 图1', 1),
((SELECT id FROM product WHERE sku='ONIGIRI-C05'), 'images/products/ONIGIRI-C05/2.svg', '《饭团小馆》第5卷 图2', 2),
((SELECT id FROM product WHERE sku='ONIGIRI-C06'), 'images/products/ONIGIRI-C06/1.svg', '《饭团小馆》第6卷 图1', 1),
((SELECT id FROM product WHERE sku='ONIGIRI-C06'), 'images/products/ONIGIRI-C06/2.svg', '《饭团小馆》第6卷 图2', 2),
((SELECT id FROM product WHERE sku='ONIGIRI-M01'), 'images/products/ONIGIRI-M01/1.svg', '饭团小馆 饭团造型抱枕 图1', 1),
((SELECT id FROM product WHERE sku='ONIGIRI-M01'), 'images/products/ONIGIRI-M01/2.svg', '饭团小馆 饭团造型抱枕 图2', 2),
((SELECT id FROM product WHERE sku='ONIGIRI-M02'), 'images/products/ONIGIRI-M02/1.svg', '饭团小馆 小馆围裙 图1', 1),
((SELECT id FROM product WHERE sku='ONIGIRI-M02'), 'images/products/ONIGIRI-M02/2.svg', '饭团小馆 小馆围裙 图2', 2),
((SELECT id FROM product WHERE sku='IRONWOOD-C01'), 'images/products/IRONWOOD-C01/1.svg', '《机械之森》第1卷 图1', 1),
((SELECT id FROM product WHERE sku='IRONWOOD-C01'), 'images/products/IRONWOOD-C01/2.svg', '《机械之森》第1卷 图2', 2),
((SELECT id FROM product WHERE sku='IRONWOOD-C01'), 'images/products/IRONWOOD-C01/3.svg', '《机械之森》第1卷 图3', 3),
((SELECT id FROM product WHERE sku='IRONWOOD-C02'), 'images/products/IRONWOOD-C02/1.svg', '《机械之森》第2卷 图1', 1),
((SELECT id FROM product WHERE sku='IRONWOOD-C02'), 'images/products/IRONWOOD-C02/2.svg', '《机械之森》第2卷 图2', 2),
((SELECT id FROM product WHERE sku='IRONWOOD-C02'), 'images/products/IRONWOOD-C02/3.svg', '《机械之森》第2卷 图3', 3),
((SELECT id FROM product WHERE sku='IRONWOOD-C03'), 'images/products/IRONWOOD-C03/1.svg', '《机械之森》第3卷 图1', 1),
((SELECT id FROM product WHERE sku='IRONWOOD-C03'), 'images/products/IRONWOOD-C03/2.svg', '《机械之森》第3卷 图2', 2),
((SELECT id FROM product WHERE sku='IRONWOOD-C03'), 'images/products/IRONWOOD-C03/3.svg', '《机械之森》第3卷 图3', 3),
((SELECT id FROM product WHERE sku='IRONWOOD-C04'), 'images/products/IRONWOOD-C04/1.svg', '《机械之森》第4卷 图1', 1),
((SELECT id FROM product WHERE sku='IRONWOOD-C04'), 'images/products/IRONWOOD-C04/2.svg', '《机械之森》第4卷 图2', 2),
((SELECT id FROM product WHERE sku='IRONWOOD-C04'), 'images/products/IRONWOOD-C04/3.svg', '《机械之森》第4卷 图3', 3),
((SELECT id FROM product WHERE sku='IRONWOOD-C05'), 'images/products/IRONWOOD-C05/1.svg', '《机械之森》第5卷 图1', 1),
((SELECT id FROM product WHERE sku='IRONWOOD-C05'), 'images/products/IRONWOOD-C05/2.svg', '《机械之森》第5卷 图2', 2),
((SELECT id FROM product WHERE sku='IRONWOOD-C06'), 'images/products/IRONWOOD-C06/1.svg', '《机械之森》第6卷 图1', 1),
((SELECT id FROM product WHERE sku='IRONWOOD-C06'), 'images/products/IRONWOOD-C06/2.svg', '《机械之森》第6卷 图2', 2),
((SELECT id FROM product WHERE sku='IRONWOOD-M01'), 'images/products/IRONWOOD-M01/1.svg', '机械之森 机械鹿拼装模型 图1', 1),
((SELECT id FROM product WHERE sku='IRONWOOD-M01'), 'images/products/IRONWOOD-M01/2.svg', '机械之森 机械鹿拼装模型 图2', 2),
((SELECT id FROM product WHERE sku='IRONWOOD-M02'), 'images/products/IRONWOOD-M02/1.svg', '机械之森 齿轮金属徽章 图1', 1),
((SELECT id FROM product WHERE sku='IRONWOOD-M02'), 'images/products/IRONWOOD-M02/2.svg', '机械之森 齿轮金属徽章 图2', 2),
((SELECT id FROM product WHERE sku='CICADA-C01'), 'images/products/CICADA-C01/1.svg', '《夏夜蝉鸣》第1卷 图1', 1),
((SELECT id FROM product WHERE sku='CICADA-C01'), 'images/products/CICADA-C01/2.svg', '《夏夜蝉鸣》第1卷 图2', 2),
((SELECT id FROM product WHERE sku='CICADA-C01'), 'images/products/CICADA-C01/3.svg', '《夏夜蝉鸣》第1卷 图3', 3),
((SELECT id FROM product WHERE sku='CICADA-C02'), 'images/products/CICADA-C02/1.svg', '《夏夜蝉鸣》第2卷 图1', 1),
((SELECT id FROM product WHERE sku='CICADA-C02'), 'images/products/CICADA-C02/2.svg', '《夏夜蝉鸣》第2卷 图2', 2),
((SELECT id FROM product WHERE sku='CICADA-C02'), 'images/products/CICADA-C02/3.svg', '《夏夜蝉鸣》第2卷 图3', 3),
((SELECT id FROM product WHERE sku='CICADA-C03'), 'images/products/CICADA-C03/1.svg', '《夏夜蝉鸣》第3卷 图1', 1),
((SELECT id FROM product WHERE sku='CICADA-C03'), 'images/products/CICADA-C03/2.svg', '《夏夜蝉鸣》第3卷 图2', 2),
((SELECT id FROM product WHERE sku='CICADA-C03'), 'images/products/CICADA-C03/3.svg', '《夏夜蝉鸣》第3卷 图3', 3),
((SELECT id FROM product WHERE sku='CICADA-C04'), 'images/products/CICADA-C04/1.svg', '《夏夜蝉鸣》第4卷 图1', 1),
((SELECT id FROM product WHERE sku='CICADA-C04'), 'images/products/CICADA-C04/2.svg', '《夏夜蝉鸣》第4卷 图2', 2),
((SELECT id FROM product WHERE sku='CICADA-C04'), 'images/products/CICADA-C04/3.svg', '《夏夜蝉鸣》第4卷 图3', 3),
((SELECT id FROM product WHERE sku='CICADA-C05'), 'images/products/CICADA-C05/1.svg', '《夏夜蝉鸣》第5卷 图1', 1),
((SELECT id FROM product WHERE sku='CICADA-C05'), 'images/products/CICADA-C05/2.svg', '《夏夜蝉鸣》第5卷 图2', 2),
((SELECT id FROM product WHERE sku='CICADA-C06'), 'images/products/CICADA-C06/1.svg', '《夏夜蝉鸣》第6卷 图1', 1),
((SELECT id FROM product WHERE sku='CICADA-C06'), 'images/products/CICADA-C06/2.svg', '《夏夜蝉鸣》第6卷 图2', 2),
((SELECT id FROM product WHERE sku='CICADA-M01'), 'images/products/CICADA-M01/1.svg', '夏夜蝉鸣 蝉鸣主题T恤 图1', 1),
((SELECT id FROM product WHERE sku='CICADA-M01'), 'images/products/CICADA-M01/2.svg', '夏夜蝉鸣 蝉鸣主题T恤 图2', 2),
((SELECT id FROM product WHERE sku='CICADA-M02'), 'images/products/CICADA-M02/1.svg', '夏夜蝉鸣 星空风铃 图1', 1),
((SELECT id FROM product WHERE sku='CICADA-M02'), 'images/products/CICADA-M02/2.svg', '夏夜蝉鸣 星空风铃 图2', 2);

-- ===== 演示数据结束 =====
