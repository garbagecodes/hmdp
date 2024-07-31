package com.hmdp.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderPaymentDTO {
    //订单号
    private Long orderId;

    //付款方式
    private Integer payType;

}
