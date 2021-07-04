package ch.algotrader.ema.services;

import ch.algotrader.ema.vo.AccInfoResponse;
import ch.algotrader.ema.vo.AccTradesResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static ch.algotrader.ema.services.AccService.AccRequest.ACCOUNT;
import static ch.algotrader.ema.services.AccService.AccRequest.TRADES;

@Service
public class AccService {

    private static final Logger logger = LoggerFactory.getLogger(AccService.class);

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final int ACC_TRADES_LIMIT = 20;
    private static final String MARKET = "MARKET";
    private static final String RESULT = "RESULT";

    @Value("${rest-uri}") private String baseUrl;
    @Value("${api-key}") private String apiKey;
    @Value("${api-secret}") private String apiSecret;

    private final RestTemplate restTemplate;
    private final ObjectMapper OM;

    public AccService() {
        this.restTemplate = new RestTemplate();

        OM = new ObjectMapper();
        OM.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        OM.configure(SerializationFeature.INDENT_OUTPUT, true);
    }

    public AccInfoResponse getInfo() throws JsonProcessingException {
        String body = getAccResponse(ACCOUNT.getLabel(), Collections.emptyMap());

        AccInfoResponse response = OM.readValue(body, AccInfoResponse.class);
        logger.info("Get Acc info response: {}", OM.writeValueAsString(response));  // indent

        return response;
    }

    public List<AccTradesResponse> getAccTradesList() throws JsonProcessingException {
        String body = getAccResponse(TRADES.label, Collections.singletonMap("symbol", "BTCUSDT"));
        logger.debug("Get my trades list: {}", body);

        TypeReference<ArrayList<AccTradesResponse>> valueTypeRef = new TypeReference<>() { };
        ArrayList<AccTradesResponse> accTradesResponses = OM.readValue(body, valueTypeRef);


        List<AccTradesResponse> trades = accTradesResponses.stream()
                .peek(t -> t.setDisplayTime(
                        LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(t.getTime()), ZoneId.systemDefault()).toString() )
                )
                .sorted(Comparator.comparing(AccTradesResponse::getTime))
                .limit(ACC_TRADES_LIMIT)
                .collect(Collectors.toList());

        logger.debug("Get my trades List<AccTradesResponse> : {}", trades);
        return trades;
    }

    private String getAccResponse(String path, Map<String, String> queryParams) {
        long time = new Date().getTime();

        final UriComponents pathUri = UriComponentsBuilder.fromUriString(path).build();
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .uriComponents(pathUri)
                .queryParam("timestamp", time);
        queryParams.forEach(builder::queryParam);

        StringBuilder sb = new StringBuilder();
        sb.append("timestamp=").append(time);

        queryParams.forEach((k,v) -> sb.append("&").append(k).append("=").append(v) );
        String signature = createHmacSignature(this.apiSecret, sb.toString());

        final URI uri = builder.queryParam("signature", signature).build().toUri();

        RequestEntity.BodyBuilder bodyBuilder = RequestEntity.method(HttpMethod.GET, uri)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON);

        bodyBuilder = bodyBuilder.header("X-MBX-APIKEY", this.apiKey);

        ResponseEntity<String> exchange = restTemplate.exchange(bodyBuilder.build(), String.class);

        return exchange.getBody();
    }

    public void sendOrder(String side, BigDecimal quantity, String symbol) {
        long time = new Date().getTime();

        final UriComponents pathUri = UriComponentsBuilder.fromUriString("order").build();
        final URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl).uriComponents(pathUri)
                .build().toUri();
        RequestEntity.BodyBuilder bodyBuilder = RequestEntity.method(HttpMethod.POST, uri)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON);

        bodyBuilder = bodyBuilder.header("X-MBX-APIKEY", this.apiKey);

        StringBuilder sb = new StringBuilder()
        .append("timestamp=").append(time)
        .append("&")
        .append("side=").append(side)
        .append("&")
        .append("quantity=").append(quantity)
        .append("&")
        .append("symbol=").append(symbol.toUpperCase(Locale.ROOT))
        .append("&")
        .append("type=").append(MARKET)
        .append("&")
        .append("newOrderRespType=").append(RESULT);

        String signature = sb + "&signature=" + createHmacSignature(this.apiSecret, sb.toString());

        ResponseEntity<String> exchange = restTemplate.exchange(bodyBuilder.body(signature), String.class);

        String bodyResp = exchange.getBody();
        logger.info("Order response: {}", bodyResp);
    }

//    private String getSignature(String payload) {
//        final String payloadBase64 = Base64.getEncoder().encodeToString(payload.getBytes());
//        return createHmacSignature(this.apiSecret, payload);
//    }

    private String createHmacSignature(String secret, String inputText) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256);
            mac.init(key);

            return new String(Hex.encodeHex(mac.doFinal(inputText.getBytes(StandardCharsets.UTF_8))));

        } catch (Exception e) {
            throw new RuntimeException("cannot create " + HMAC_SHA_256, e);
        }
    }

    enum AccRequest {
        TRADES("myTrades"),
        ACCOUNT("account");


        private static final Map<String, AccRequest> BY_LABEL = new HashMap<>();
        private final String label;

        AccRequest(String label) {
            this.label = label;
        }

        static {
            for (AccRequest element : values()) {
                BY_LABEL.putIfAbsent(element.label, element);
            }
        }

        String getLabel() {
            return this.label;
        }

        static AccRequest getByLabel(String label) {
            return BY_LABEL.get(label);
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

}
