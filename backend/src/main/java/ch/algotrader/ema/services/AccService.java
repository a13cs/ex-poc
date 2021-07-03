package ch.algotrader.ema.services;

import ch.algotrader.ema.vo.AccResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.Date;
import java.util.Locale;

@Service
public class AccService {

    private static final Logger logger = LoggerFactory.getLogger(AccService.class);
    private static final String HMAC_SHA_256 = "HmacSHA256";

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

    public AccResponse getInfo() throws JsonProcessingException {
        long time = new Date().getTime();

        final UriComponents pathUri = UriComponentsBuilder.fromUriString("account").build();
        final URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl).uriComponents(pathUri)
                .queryParam("timestamp", time)
                .queryParam("signature", getSignature("timestamp=" + time))
                .build().toUri();
        RequestEntity.BodyBuilder bodyBuilder = RequestEntity.method(HttpMethod.GET, uri)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON);

//        RequestEntity<String> body = bodyBuilder.body(signed);
        bodyBuilder = bodyBuilder.header("X-MBX-APIKEY", this.apiKey);

        ResponseEntity<String> exchange = restTemplate.exchange(bodyBuilder.build(), String.class);
        String body = exchange.getBody();
        logger.info("Get Acc info response: {}", body);

        return OM.readValue(body, AccResponse.class);
    }

    public void sendOrder(String side, BigDecimal quantity, String symbol) {
        long time = new Date().getTime();

        final UriComponents pathUri = UriComponentsBuilder.fromUriString("order").build();
        final URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl).uriComponents(pathUri)
//                .queryParam("timestamp", time)
//                .queryParam("newOrderRespType", "RESULT")
//                .queryParam("side", side)
//                .queryParam("quantity", quantity)
//                .queryParam("symbol", symbol)
//                .queryParam("signature", getSignature("timestamp=" + time))
                .build().toUri();
        RequestEntity.BodyBuilder bodyBuilder = RequestEntity.method(HttpMethod.POST, uri)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON);

        StringBuilder sb = new StringBuilder();
        sb.append("timestamp=").append(time).append("&");
        sb.append("side=").append(side).append("&");
        sb.append("quantity=").append(quantity).append("&");

        sb.append("type=" + "MARKET" + "&");
        sb.append("newOrderRespType=" + "RESULT&");
        sb.append("symbol=" + symbol.toUpperCase(Locale.ROOT));

        bodyBuilder = bodyBuilder.header("X-MBX-APIKEY", this.apiKey);

//        ResponseEntity<String> exchange = restTemplate.exchange(bodyBuilder.build(), String.class);
        ResponseEntity<String> exchange = restTemplate.exchange(bodyBuilder.body(sb + "&signature=" + getSignature(sb.toString())), String.class);
        String bodyResp = exchange.getBody();
        logger.info("Test Order response: {}", bodyResp);
    }

    private String getSignature(String payload) {
//        final String payloadBase64 = Base64.getEncoder().encodeToString(payload.getBytes());
        return createHmacSignature(this.apiSecret, payload);
    }

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
}
