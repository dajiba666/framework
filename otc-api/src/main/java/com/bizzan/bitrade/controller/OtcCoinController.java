package com.bizzan.bitrade.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.bizzan.bitrade.coin.CoinExchangeFactory;
import com.bizzan.bitrade.service.OtcCoinService;
import com.bizzan.bitrade.util.MessageResult;

import static com.bizzan.bitrade.util.MessageResult.success;

import java.util.List;
import java.util.Map;

/**
 * @author GS
 * @date 2018年01月06日
 */
@RestController
@Slf4j
@RequestMapping(value = "/coin")
public class OtcCoinController extends BaseController {

    @Autowired
    private OtcCoinService coinService;
    @Autowired
    private CoinExchangeFactory coins;

    /**
     * 取得正常的币种
     *
     * @return
     */
    @RequestMapping(value = "all")
    public MessageResult allCoin() throws Exception {
        List<Map<String, String>> list = coinService.getAllNormalCoin();
        list.stream().forEachOrdered(x -> {
            if (coins.get(x.get("unit")) != null) {
                x.put("marketPrice", coins.get(x.get("unit")).toString());
            }
        });
        MessageResult result = success();
        result.setData(list);
        return result;
    }

    @RequestMapping(value = "unit/{unit}", method = RequestMethod.GET)
    public MessageResult unit(@PathVariable("unit") String unit) {
        return success(coinService.findByUnit(unit));
    }
}
