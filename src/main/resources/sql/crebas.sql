-- ----------------------------
-- Table structure for speedkill
-- ----------------------------
DROP TABLE IF EXISTS `speedkill`;
CREATE TABLE `speedkill` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
    `name` varchar(120) NOT NULL COMMENT '秒杀商品',
    `number` int NOT NULL COMMENT '库存数量',
    `start_time` timestamp NOT NULL COMMENT '秒杀开始时间',
    `end_time` timestamp NOT NULL COMMENT '秒杀结束时间',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_start_time` (`start_time`),
    KEY `idx_end_time` (`end_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=1001 DEFAULT CHARSET=utf8 COMMENT='秒杀库存表';


INSERT INTO `speedkill`(`id`, `name`, `number`, `start_time`, `end_time`, `create_time`) VALUES (1000, 'iphone12 128g 黑色', 1000000, '2020-11-20 16:00:00', '2020-11-21 16:00:00', '2020-11-19 16:00:00');


DROP TABLE IF EXISTS `order`;
CREATE TABLE `order` (
     `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
     `no` varchar(120) NOT NULL COMMENT '订单号',
     `product_id` bigint NOT NULL COMMENT '秒杀商品id',
     `status` int(4) NOT NULL COMMENT '秒杀状态',
     `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
     PRIMARY KEY (`id`),
     UNIQUE KEY `idx_no` (`no`),
     KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=1001 DEFAULT CHARSET=utf8 COMMENT='订单表';

SET FOREIGN_KEY_CHECKS = 1;

