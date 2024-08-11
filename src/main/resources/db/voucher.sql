SET NAMES utf8mb4;
SET
FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `tb_limit_voucher`;
CREATE TABLE `tb_limit_voucher`
(
    `voucher_id`  bigint(20) NOT NULL COMMENT '关联的优惠券的id',
    `stock`       int(11) DEFAULT NULL COMMENT '库存',
    `limit_count` int(11) DEFAULT NULL COMMENT '限购数量',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`voucher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='限购优惠券表';

DROP TABLE IF EXISTS `tb_common_voucher`;
CREATE TABLE `tb_common_voucher`
(
    `voucher_id`  bigint(20) NOT NULL COMMENT '关联的优惠券的id',
    `stock`       int(11) DEFAULT NULL COMMENT '库存',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`voucher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='普通优惠券表';