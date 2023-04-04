package com.tradebot;

import org.apache.commons.codec.binary.Hex;
import org.junit.Assert;
import org.junit.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class BinanceApiServiceImplTest {
    private final static String API_KEY = "uHyxBHzr7wXstmj5fJgppModqVkiimJQuNnSS7rQAMyvTno0RwTGvqX9pserTxYg";
    private final static String SECRET_KEY = "00t0EpmA3Qd2ZyMTtbzl0fCro0jWTMX3Gocax6NoxkjF8Qclkfv53jDP7L4ugBHw";
    private static final String BINANCE_BASE_URL = "https://api.binance.com";
    private static final String PLACE_ORDER_URL = "/api/v3/order/test";
    private static long timestamp = System.currentTimeMillis();
    private static long recvWindow = 60_000L;
    private final String symbol = "BTCUSDT";

    @Test
    public void testPlaceOrder() throws Exception {
        String method = "POST";
        String price = "11400";
        String quantity = "10.08";
        String side = "SELL";
        String payload;

        payload = "symbol=" + symbol + "&side=" + side + "&price=" + price + "&quantity=" + quantity + "&type=LIMIT&timeInForce=GTC" + "&recvWindow=" + recvWindow + "&timestamp=" + timestamp;
        URL url = new URL(BINANCE_BASE_URL + PLACE_ORDER_URL + "?" + payload + "&signature=" + sign(payload, SECRET_KEY));
        System.out.println(url.toString());
        HttpURLConnection connection = getConnectionWithProperties(url, API_KEY, method);
        Assert.assertEquals(200, connection.getResponseCode());
    }

    private HttpURLConnection getConnectionWithProperties(URL url, String apiKey, String method) throws Exception{
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        con.setRequestMethod(method);
        con.setRequestProperty("X-MBX-APIKEY", apiKey);
        System.out.println(con.getResponseCode());
        System.out.println(con.getResponseMessage());
        return con;
    }

    private String getResponse(HttpURLConnection connection) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuffer content = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        return content.toString();
    }

    private void checkResponseStatus(HttpURLConnection connection) throws Exception {
        if (connection.getResponseCode() != 200){
            System.out.println("Error code: " + connection.getResponseCode());
            System.out.println("Error message: " + connection.getResponseMessage());
            throw new Exception("getConnectionWithProperties error ");
        }
    }

    private static String sign(String payload, String secretKey) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secretKeySpec);
            return new String(Hex.encodeHex(sha256_HMAC.doFinal(payload.getBytes())));
        } catch (Exception e) {
            throw new RuntimeException("Unable to sign message.", e);
        }
    }
}
