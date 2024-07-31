package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.CommonVoucher;
import com.hmdp.mapper.CommonVoucherMapper;
import com.hmdp.service.ICommonVoucherService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 普通优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 */
@Service
public class CommonVoucherServiceImpl extends ServiceImpl<CommonVoucherMapper, CommonVoucher> implements ICommonVoucherService {

}
