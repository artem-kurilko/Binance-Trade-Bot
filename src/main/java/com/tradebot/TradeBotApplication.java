package com.tradebot;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.ibm.icu.impl.Utility.parseInteger;
import static com.tradebot.service.OrdersService.*;
import static java.lang.Float.parseFloat;

/**
 * Binance bot application.
 *
 * @author Artemii Kurilko
 * @version 1.0
 */
//FIXME:
// MARKET DROP COEFFICIENT set
// remove time sleep
// set btc usd redistribution, when usdt is low
@Slf4j
public class TradeBotApplication {

    public static void main(String[] args) throws Exception {
        log.info("Bot is running...");
//        runAlgorithm();
    }

    private static void runAlgorithm() throws InterruptedException {
        try {
            while (true) {
                JSONArray activeOrders = getActiveOrders();
                if (activeOrders.length() != 0) {
                    try {
                        JSONObject actOrder = getLastActiveOrder();
                        if (actOrder.getString("side").equals("sell")) {
                            // check that price didn't drop >= 5%, if did then place additional buy order
                            float sellPrice = actOrder.getFloat("price");
                            if (sellPrice * 0.94 >= getAveragePrice())
                                placeAdditionalBuyOrder();
                        } else {
                            float cumQuantity = parseFloat(actOrder.getString("quantity_cumulative"));
                            if (cumQuantity == 0.0)
                                checkOrderLifeTime(actOrder);
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage());
                    }
                } else {
                    log.info("No active orders. Place order.");
                    try {
                        String side = getLastOrdersHistory().getString("side");
                        boolean orderSide = side.equals("SELL");
                        placeOrder(orderSide);
                    } catch (Exception e) {
                        log.error(e.getMessage());
                    }
                }
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            log.info("Connection error. " + e.getMessage());
            Thread.sleep(10000);
            runAlgorithm();
        }
    }
}
