package ch.algotrader.ema.services;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.algotrader.ema.vo.NewOrderRequest;
import ch.algotrader.ema.vo.OrderResponse;
import ch.algotrader.ema.vo.Request;

@Service
public class TradingService {

    private static final Logger logger = LoggerFactory.getLogger(TradingService.class);

    @Value("${rest-uri}") private String baseUrl;
    @Value("${api-key}") private String apiKey;
    @Value("${api-secret}") private String apiSecret;

    private final RestTemplate restTemplate;
    private final AtomicLong nonce = new AtomicLong(System.currentTimeMillis() * 1000);

    public TradingService() {
        this.restTemplate = new RestTemplate();
    }

    // todo
    public void sendOrder(String side, BigDecimal quantity, String symbol) {
        try {
//            $ echo -n "symbol=LTCBTC&side=BUY&type=LIMIT&timeInForce=GTC&quantity=1&price=0.1&recvWindow=5000&timestamp=1499827319559" | openssl dgst -sha256 -hmac "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"
//            (stdin)= c8db56825ae71d6d79447849e617115f4a920fa2acdcab2b053c4b2838bd6b71
//
//            curl command:
//
//            (HMAC SHA256)
//            $ curl -H "X-MBX-APIKEY: vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A" -X POST 'https://api.binance.com/api/v3/order' -d 'symbol=LTCBTC&side=BUY&type=LIMIT&timeInForce=GTC&quantity=1&price=0.1&recvWindow=5000&timestamp=1499827319559&signature=c8db56825ae71d6d79447849e617115f4a920fa2acdcab2b053c4b2838bd6b71'
//

            final NewOrderRequest bncRequest = createNewBFXOrder(side, quantity, symbol);
            final RequestEntity<String> post = createPost("order/test", bncRequest);
            ResponseEntity<OrderResponse> response = restTemplate.exchange(post, OrderResponse.class);
            logger.info("executed market order ", response.getBody().toString());
        } catch (RestClientResponseException e) {
            String msg = e.getResponseBodyAsString();
            logger.error(msg, e);
        } catch (RestClientException e) {
            logger.error("rest exception", e);
        }
    }

    private NewOrderRequest createNewBFXOrder(String side, BigDecimal quantity, String symbol) {
        NewOrderRequest orderRequest = new NewOrderRequest();
        orderRequest.setSymbol(symbol);
        orderRequest.setAmount(String.valueOf(quantity));
        orderRequest.setSide(side);
        orderRequest.setPrice(String.valueOf(Math.random()));
        orderRequest.setType("exchange market");
        orderRequest.setExchange("bitfinex");

        return orderRequest;
    }

    private RequestEntity<String> createPost(String path, Request pld) {
        String payloadStr = transformPayload(path, pld);
        return getRequestEntity(path, payloadStr, HttpMethod.POST);
    }

    private RequestEntity<String> getRequestEntity(String path, String payloadStr, HttpMethod method) {
        final Map<String, String> payloadHdr = createAuthHttpHeaders(payloadStr);

        final UriComponents pathUri = UriComponentsBuilder.fromUriString(path).build();
        final URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl).uriComponents(pathUri).build().toUri();
        RequestEntity.BodyBuilder bodyBuilder = RequestEntity.method(method, uri).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON);

        for (Map.Entry<String, String> entry : payloadHdr.entrySet()) {
            bodyBuilder = bodyBuilder.header(entry.getKey(), entry.getValue());
        }

        return bodyBuilder.body(payloadStr);
    }

    private String transformPayload(String path, Request payload) {
        String relativePath = UriComponentsBuilder.fromHttpUrl(baseUrl).build().getPath() + path;
        payload.setRequest(relativePath);
        payload.setNonce(String.valueOf(nonce.incrementAndGet()));

        String payloadStr;
        try {
            payloadStr = new ObjectMapper().writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return payloadStr;
    }

    private Map<String, String> createAuthHttpHeaders(String payload) {
        final String payloadBase64 = Base64.getEncoder().encodeToString(payload.getBytes());

        String payloadHmacSHA384 = createHmacSignature(this.apiSecret, payloadBase64, "HmacSHA384");

        Map<String, String> hdrs = new HashMap<>();
        hdrs.put("X-BFX-APIKEY", this.apiKey);
        hdrs.put("X-BFX-PAYLOAD", payloadBase64);
        hdrs.put("X-BFX-SIGNATURE", payloadHmacSHA384);
        return hdrs;
    }

    private String createHmacSignature(String secret, String inputText, String algoName) {
        try {
            Mac mac = Mac.getInstance(algoName);
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algoName);
            mac.init(key);

            return new String(Hex.encodeHex(mac.doFinal(inputText.getBytes(StandardCharsets.UTF_8))));

        } catch (Exception e) {
            throw new RuntimeException("cannot create " + algoName, e);
        }
    }

}
