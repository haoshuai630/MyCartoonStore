-- 把周边商品名统一为《作品名》周边名（单行本本来就是《作品名》第N卷）
-- 用法：mysql -u root -p --default-character-set=utf8mb4 < db/03-rename-merch.sql
USE MyCartoonStore;

UPDATE product SET name = '《星轨列车》列车长亚克力立牌' WHERE sku = 'STARLINE-M01';
UPDATE product SET name = '《星轨列车》车票金属徽章' WHERE sku = 'STARLINE-M02';
UPDATE product SET name = '《星轨列车》星空帆布袋' WHERE sku = 'STARLINE-M03';
UPDATE product SET name = '《星轨列车》线路图线圈本' WHERE sku = 'STARLINE-M04';
UPDATE product SET name = '《星轨列车》列车钥匙扣' WHERE sku = 'STARLINE-M05';
UPDATE product SET name = '《星轨列车》全彩设定画集' WHERE sku = 'STARLINE-M06';
UPDATE product SET name = '《雾港侦探》侦探风衣立牌' WHERE sku = 'MISTPORT-M01';
UPDATE product SET name = '《雾港侦探》雾港地图海报' WHERE sku = 'MISTPORT-M02';
UPDATE product SET name = '《雾港侦探》怀表造型挂件' WHERE sku = 'MISTPORT-M03';
UPDATE product SET name = '《雾港侦探》案件档案文件夹' WHERE sku = 'MISTPORT-M04';
UPDATE product SET name = '《雾港侦探》主角徽章套装' WHERE sku = 'MISTPORT-M05';
UPDATE product SET name = '《雾港侦探》雾港设定集' WHERE sku = 'MISTPORT-M06';
UPDATE product SET name = '《饭团小馆》饭团造型抱枕' WHERE sku = 'ONIGIRI-M01';
UPDATE product SET name = '《饭团小馆》小馆围裙' WHERE sku = 'ONIGIRI-M02';
UPDATE product SET name = '《饭团小馆》菜单贴纸包' WHERE sku = 'ONIGIRI-M03';
UPDATE product SET name = '《饭团小馆》陶瓷马克杯' WHERE sku = 'ONIGIRI-M04';
UPDATE product SET name = '《饭团小馆》店长亚克力立牌' WHERE sku = 'ONIGIRI-M05';
UPDATE product SET name = '《饭团小馆》料理手账本' WHERE sku = 'ONIGIRI-M06';
UPDATE product SET name = '《机械之森》机械鹿拼装模型' WHERE sku = 'IRONWOOD-M01';
UPDATE product SET name = '《机械之森》齿轮金属徽章' WHERE sku = 'IRONWOOD-M02';
UPDATE product SET name = '《机械之森》森林夜光海报' WHERE sku = 'IRONWOOD-M03';
UPDATE product SET name = '《机械之森》工装短T' WHERE sku = 'IRONWOOD-M04';
UPDATE product SET name = '《机械之森》机械书签套装' WHERE sku = 'IRONWOOD-M05';
UPDATE product SET name = '《机械之森》机械设定画集' WHERE sku = 'IRONWOOD-M06';
UPDATE product SET name = '《夏夜蝉鸣》蝉鸣主题T恤' WHERE sku = 'CICADA-M01';
UPDATE product SET name = '《夏夜蝉鸣》星空风铃' WHERE sku = 'CICADA-M02';
UPDATE product SET name = '《夏夜蝉鸣》蝉翼亚克力挂件' WHERE sku = 'CICADA-M03';
UPDATE product SET name = '《夏夜蝉鸣》夏日明信片组' WHERE sku = 'CICADA-M04';
UPDATE product SET name = '《夏夜蝉鸣》主角亚克力立牌' WHERE sku = 'CICADA-M05';
UPDATE product SET name = '《夏夜蝉鸣》夏日画集' WHERE sku = 'CICADA-M06';

-- 核对：SELECT sku, name FROM product WHERE sku LIKE '%-M%' ORDER BY sku;