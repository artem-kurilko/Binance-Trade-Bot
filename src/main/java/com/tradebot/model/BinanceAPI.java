package com.tradebot.model;

public interface BinanceAPI {
    String BINANCE_BASE_URL = "https://api.binance.com",
    BINANCE_ORDER_URL = BINANCE_BASE_URL + "/api/v3/order",
    BINANCE_ACTIVE_ORDERS_URL = BINANCE_BASE_URL + "/api/v3/openOrders",
    BINANCE_TRADE_HISTORY_URL = BINANCE_BASE_URL + "/api/v3/myTrades",
    BINANCE_BALANCE_URL = BINANCE_BASE_URL + "/sapi/v3/asset/getUserAsset",
    BINANCE_AVERAGE_PRICE_URL = BINANCE_BASE_URL + "/api/v3/avgPrice";
}
