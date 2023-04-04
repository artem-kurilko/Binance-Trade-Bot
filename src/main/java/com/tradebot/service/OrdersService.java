package com.tradebot.service;

import com.tradebot.model.Currencies;
import com.tradebot.security.HmacSHA256Signer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

import static com.tradebot.model.BinanceAPI.*;
import static java.lang.Float.parseFloat;

@Slf4j
public final class OrdersService {
    private static final String API = "";
    private static final String SECRET_KEY = "";
    private static final String CURRENCY_PAIR = "BTCUSDT";
    private static final float BUY_PRICE_COEFFICIENT = (float) 0.998;
    private static final float SELL_PRICE_COEFFICIENT = (float) 1.004;
    private static final float MARKET_DROP_COEFFICIENT = (float) 1.01;

    private static final OkHttpClient client = new OkHttpClient.Builder().build();
    private static final long timestamp = System.currentTimeMillis();
    private static final long recvWindow = 60_000L;

    public static void placeOrder(boolean isBuy) throws Exception {
        String side = isBuy ? "BUY" : "SELL";
        float price, quantity;
        if (isBuy) {
            price = getAveragePrice() * BUY_PRICE_COEFFICIENT;
            quantity = (getCurrencyBalance(Currencies.USDT) / 4) / price;
        } else {
            float curPrice = getAveragePrice();
            float sellPrice = getLastOrdersHistory().getFloat("price") * SELL_PRICE_COEFFICIENT;
            price = Math.max(sellPrice, curPrice);
            quantity = getLastOrdersHistory().getFloat("qty");
        }
        if (quantity == 0.0)
            throw new Exception("QUANTITY IS ZERO. SIDE " + side);
        createOrder(side, price, quantity);
        log.info("Placed order - side: {}, price: {}, quantity: {}", side, price, quantity);
    }

    public static void placeAdditionalBuyOrder() throws Exception {
        float quantity = getLastActiveOrder().getFloat("qty");
        float price = parseFloat(String.valueOf(getAveragePrice() * MARKET_DROP_COEFFICIENT));
        String side = "BUY";
        createOrder(side, price, quantity);
        log.info("Placed additional buy order - price: {}, quantity: {}", price, quantity);
    }

    public static void createOrder(String side, float price, float quantity) throws IOException {
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(BINANCE_ORDER_URL)).newBuilder();
        urlBuilder.addQueryParameter("symbol", CURRENCY_PAIR);
        urlBuilder.addQueryParameter("side", side);
        urlBuilder.addQueryParameter("price", String.valueOf((int) price));
        urlBuilder.addQueryParameter("quantity", String.valueOf(quantity));
        urlBuilder.addQueryParameter("type", "LIMIT");
        urlBuilder.addQueryParameter("timeInForce", "GTC");
        addTimestampAndRecvWindow(urlBuilder);
        signRequest(urlBuilder);

        Response response = getResponse(urlBuilder,"POST",true);
        checkResponseStatus(response,"Exception while placing binance " + side + " order.");
        log.info("place " + side + " order.");
        assert response.body() != null : "Response body is null";
        response.body().close();
    }

    public static float getTotalBalance() throws Exception {
        float usdtBalance = getUSDTBalance();
        float btcBalance = getBTCBalance() * getAveragePrice();
        return usdtBalance+btcBalance;
    }

    public static JSONArray getActiveOrders() throws IOException {
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(BINANCE_ACTIVE_ORDERS_URL)).newBuilder();
        addTimestampAndRecvWindow(urlBuilder);
        signRequest(urlBuilder);
        Response response = getResponse(urlBuilder,"GET",true);
        checkResponseStatus(response,"Error while getting active orders.");
        assert response.body() != null : "Response body is null";
        JSONArray activeOrders = new JSONArray(response.body().string());
        response.body().close();
        return activeOrders;
    }

    public static JSONObject getLastActiveOrder() throws Exception{
        if (getActiveOrders().length() >= 1)
            return getActiveOrders().getJSONObject(0);
        else return new JSONObject();
    }

    public static JSONArray getOrdersHistory() throws Exception {
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(BINANCE_TRADE_HISTORY_URL)).newBuilder();
        addTimestampAndRecvWindow(urlBuilder);
        urlBuilder.addQueryParameter("symbol", CURRENCY_PAIR);
        signRequest(urlBuilder);
        Response response = getResponse(urlBuilder,"GET",true);
        checkResponseStatus(response, "Error while getting orders history.");
        try {
            assert response.body() != null : "Response body is null";
            JSONArray orderHistory = new JSONArray(response.body().string());
            response.body().close();
            return orderHistory;
        } catch (NullPointerException e){
            log.info("Error while getting orders history. " + e.toString());
        }
        return new JSONArray();
    }

    public static JSONObject getLastOrdersHistory(){
        try {
            int orderIndex = getOrdersHistory().length()-1;
            return getOrdersHistory().getJSONObject(orderIndex);
        } catch (Exception e){
            log.info("Error while getting last order history");
        }
        return new JSONObject();
    }

    public static void checkOrderLifeTime(JSONObject order) throws Exception {
        long createdAt = order.getLong("time");
        long current = System.currentTimeMillis();
        int ordLifetime = 60000; // 1 minute
        if (createdAt + ordLifetime == current || createdAt + ordLifetime < current) {
            log.info("Cancel expired buy order.");
            cancelOrder(order);
        }
    }

    public static void cancelOrder(JSONObject order) throws Exception {
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(BINANCE_ORDER_URL)).newBuilder();
        addTimestampAndRecvWindow(urlBuilder);
        String clientOrderId = order.getString("clientOrderId");
        urlBuilder.addQueryParameter("origClientOrderId", clientOrderId);
        urlBuilder.addQueryParameter("symbol", CURRENCY_PAIR);
        signRequest(urlBuilder);
        Response response = getResponse(urlBuilder,"DELETE",true);
        checkResponseStatus(response,"Error while canceling order: " + clientOrderId);
        log.info("order has been canceled, clientOrderId: " + clientOrderId);
        assert response.body() != null : "Response body is null";
        response.body().close();
    }

    public static float getAveragePrice() throws Exception {
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(BINANCE_AVERAGE_PRICE_URL)).newBuilder();
        urlBuilder.addQueryParameter("symbol", CURRENCY_PAIR);
        Response response = getResponse(urlBuilder,"GET",false);
        checkResponseStatus(response,"Error while getting average price.");
        assert response.body() != null : "Response body is null";
        JSONObject avgPrice = new JSONObject(response.body().string());
        response.body().close();
        return avgPrice.getFloat("price");
    }

    public static float getCurrencyBalance(String symbol) throws Exception {
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(BINANCE_BALANCE_URL)).newBuilder();
        urlBuilder.addQueryParameter("asset", symbol);
        addTimestampAndRecvWindow(urlBuilder);
        signRequest(urlBuilder);
        Response response = getResponse(urlBuilder,"POST",true);
        checkResponseStatus(response,"Exception while getting currency balance.");
        assert response.body() != null : "Response body is null";
        float balance = 0.0f;
        try {
            JSONObject responseBody = new JSONArray(response.body().string()).getJSONObject(0);
            balance = responseBody.getFloat("free");
        } catch (Exception e) {}
        response.body().close();
        return balance;
    }

    public static float getBTCBalance() throws Exception {
        return getCurrencyBalance("BTC");
    }

    public static float getUSDTBalance() throws Exception {
        return getCurrencyBalance("USDT");
    }

    /**
     * Adds timestamp and recvWindow parameters to request
     * @param urlBuilder HttpUrl.Builder instance
     */
    private static void addTimestampAndRecvWindow(HttpUrl.Builder urlBuilder){
        urlBuilder.addQueryParameter("recvWindow", String.valueOf(recvWindow));
        urlBuilder.addQueryParameter("timestamp", String.valueOf(timestamp));
    }

    /**
     * Adds signature paramter to request
     * @param urlBuilder HttpUrl.Builder instance
     */
    private static void signRequest(HttpUrl.Builder urlBuilder){
        String url = urlBuilder.build().toString();
        urlBuilder.addQueryParameter("signature", HmacSHA256Signer.sign(getQueryParameters(url), SECRET_KEY));
    }

    /**
     * Returns request's parameters
     * @param url request link
     * @return string value
     */
    private static String getQueryParameters(String url){
        String[] queryParts = url.split("\\?");
        return queryParts[1];
    }

    /**
     * Creates request from urlBuilder and receive response.
     * @param urlBuilder request address
     * @param requiredAuthorization boolean value if api key header is needed
     * @return Response instance
     */
    private static Response getResponse(HttpUrl.Builder urlBuilder, String method, boolean requiredAuthorization) throws IOException {
        String url = urlBuilder.build().toString();
        final RequestBody formBody = new FormBody.Builder().build();

        Request request;
        if (!requiredAuthorization)
            request = new Request.Builder().url(url).build();
        else {
            switch (method){
                case "GET":
                    request = new Request.Builder().url(url).addHeader("X-MBX-APIKEY", API).build();
                    break;

                case "POST":
                    request = new Request.Builder().url(url).addHeader("X-MBX-APIKEY", API).post(formBody).build();
                    break;

                case "DELETE":
                    request = new Request.Builder().url(url).addHeader("X-MBX-APIKEY", API).delete().build();
                    break;

                default:
                    throw new IOException("Error while getting response. Http method " + method + " not found.");
            }
        }
        Call call = client.newCall(request);
        return call.execute();
    }

    /**
     * Checks response status and log result.
     * @param response request response
     * @param errorMessage string message
     */
    private static void checkResponseStatus(Response response, String errorMessage){
        if (!response.isSuccessful())
            log.info(errorMessage + " Error code: " + response.code() + " Error messaage: " + response.message());
    }

}
