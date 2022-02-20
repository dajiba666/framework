package com.bizzan.bitrade.job;

import com.bizzan.bitrade.constant.AdvertiseType;
import com.bizzan.bitrade.constant.AppealStatus;
import com.bizzan.bitrade.dao.OtcOrderExpireDao;
import com.bizzan.bitrade.entity.*;
import com.bizzan.bitrade.event.OrderEvent;
import com.bizzan.bitrade.exception.InformationExpiredException;
import com.bizzan.bitrade.service.*;
import com.bizzan.bitrade.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author GS
 * @date 2018年01月22日
 */
@Component
@Slf4j
public class CheckOrderTask {
    @Autowired
    private OrderService orderService;
    @Autowired
    private OtcOrderExpireService otcOrderExpireService;
    @Autowired
    private OtcOrderExpireDao otcOrderExpireDao;
    @Autowired
    private InvestmentMessageService investmentMessageService;
    @Autowired
    private InvestmentMemberStatusService investmentMemberStatusService;
    @Autowired
    private OrderEvent orderEvent;
    @Autowired
    private AppealService appealService;

    @Scheduled(fixedRate = 60000)
    @Transactional(rollbackFor = Exception.class)
    public void checkExpireOrder() {
        List<InvestmentMemberStatus> investmentMemberStatuses = investmentMemberStatusService.findByStatusAndTimeIsGreaterThanEqual(1, new Date());
        for (InvestmentMemberStatus investmentMemberStatus : investmentMemberStatuses) {
            investmentMemberStatus.setStatus(0);
        }
        investmentMemberStatusService.saveAll(investmentMemberStatuses);
        List<Order> list = orderService.checkExpiredOrder();
        log.info("=========检查订单支付超时，确认超时逻辑===========");
        //未付款订单
        for (Order order : list) {
            Long customerId = order.getCustomerId();
            String date = DateUtil.getDate();
        }
        List<Order> orderList = orderService.checkPayOrder();
        for (Order order : orderList) {
            Long customerId = order.getCustomerId();
            String date = DateUtil.getDate();
            OtcOrderExpire otcOrderExpire = otcOrderExpireService.findById(customerId, date);
            if (otcOrderExpire == null) {
                otcOrderExpire = new OtcOrderExpire();
                otcOrderExpire.setNowDate(date);
                Member member = new Member();
                member.setId(customerId);
                otcOrderExpire.setMember(member);
                otcOrderExpire.setPayCount(0);
                otcOrderExpire.setExchangeCount(0);
                otcOrderExpireService.create(otcOrderExpire);
            }
            otcOrderExpireDao.incPayCount(customerId);
        }
        log.info("=========检查订单支付超时，确认超时逻辑结束===========");
        orderList.stream().forEach(order -> {
            try {
                Appeal appeal = appealService.findByOrderId(order.getId());
                if (appeal != null && appeal.getStatus().equals(AppealStatus.NOT_PROCESSED)) {
                    return;
                }
                orderService.release(order.getOrderSn(), order.getMemberId());
                orderEvent.onOrderCompleted(order);
            } catch (InformationExpiredException e) {
                e.printStackTrace();
            }

        });

        log.info("=========开始检查过期订单===========");
        list.stream().forEach(x -> {
                    try {
                        Appeal appeal = appealService.findByOrderId(x.getId());
                        if (appeal != null && appeal.getStatus().equals(AppealStatus.NOT_PROCESSED)) {
                            return;
                        }
                        if (x.getAdvertiseType().equals(AdvertiseType.BUY)) {
                            //代表该会员是广告发布者，购买类型的广告，并且是付款者
                            orderService.cancelOrder(x, x.getMemberId());
                        } else if (x.getAdvertiseType().equals(AdvertiseType.SELL)) {
                            //代表该会员不是广告发布者，并且是付款者
                            orderService.cancelOrder(x, x.getCustomerId());
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        log.warn("订单编号{}:自动取消失败", x.getOrderSn());
                    }
                }
        );
        log.info("=========检查过期订单结束===========");
    }
}
