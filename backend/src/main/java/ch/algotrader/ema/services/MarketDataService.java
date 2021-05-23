package ch.algotrader.ema.services;


import ch.algotrader.ema.strategy.StrategyLogic;
import ch.algotrader.ema.ws.JSONTextDecoder;
import ch.algotrader.ema.ws.JSONTextEncoder;
import ch.algotrader.ema.ws.model.AggTradeEvent;
import ch.algotrader.ema.ws.model.ChannelSubscription;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
@ClientEndpoint(encoders = {JSONTextEncoder.class}, decoders = {JSONTextDecoder.class})
public class MarketDataService implements DisposableBean, InitializingBean {

    private static final Logger LOGGER = LogManager.getLogger(MarketDataService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
        objectMapper.configure(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS, true);

        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        objectMapper.configure(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED, true);
    }

    private String topic;

    @Value("${api-secret}") private String apiSecret;

    private final StrategyLogic strategyLogic;
    private final BinanceApiWebSocketClient client = BinanceApiClientFactory.newInstance().newWebSocketClient();

    @Value("${ws-uri}")
    private String wsUrl;

    private Session session;

    @Autowired
    public MarketDataService(StrategyLogic strategyLogic) {
        this.strategyLogic = strategyLogic;
    }

    public void subscribeTrades(String topic) {
        if(isBlank(topic)) this.topic = topic;
        try {
            if (this.session == null || ! this.session.isOpen()) {
                this.session = initSession();
            }
            ChannelSubscription subscription = ChannelSubscription.trades(topic);
//            subscription.setSignature(createSignature(subscription));

            final String ser = objectMapper.writeValueAsString(subscription);
            LOGGER.info("sending " + ser);
            this.session.getBasicRemote().sendText(ser);

            String list = objectMapper.writeValueAsString(ChannelSubscription.list());
            this.session.getBasicRemote().sendText(list);
            LOGGER.info("sending " + list);

        } catch (IOException e) {
            LOGGER.error(e);
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        LOGGER.info("Connected: " + session.getNegotiatedSubprotocol());
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        LOGGER.info("Closed, reason: {}", closeReason);
    }

    @OnMessage
    public void onMessage(Session session, String msg) {
        LOGGER.info("msg {}", msg);
        try {
            HashMap<String, String> map = objectMapper.readValue(msg, new TypeReference<HashMap<String, String>>() {
            });

            if (map.get("id") != null && "aggTrade".equals(map.get("e"))) {
                final AggTradeEvent tradeEvent = AggTradeEvent.fromJson(map);
                strategyLogic.handleTradeEvent(tradeEvent);
            } else if (map.get("result") != null) {
                // pb
            }
        } catch (JsonProcessingException jpe) {
            // ignore
        } catch (Exception e) {
            LOGGER.warn("Could not read exchange message {}. Err: {}", msg, e);
        }
    }

    @Override
    public void destroy() throws Exception {
        LOGGER.info("Shutting down web socket session");
        this.session.close();
    }

    @Override
    public void afterPropertiesSet() {
        this.session = initSession();
    }

    private Session initSession() {
        Session ssn = null;
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        try {
            ssn = container.connectToServer(this, URI.create(wsUrl + "btcusdt" + "@aggTrade"));
            LOGGER.info("session open: " + ssn.isOpen());

        } catch (DeploymentException | IOException e) {
            LOGGER.error(e);
        }
        return ssn;
    }

    private String createSignature(ChannelSubscription subscription) throws JsonProcessingException {
        String inputText = new ObjectMapper().writeValueAsString(subscription);
        return createHmacSignature(apiSecret, inputText, "HmacSHA256");
    }

    private String createHmacSignature(String secret, String inputText, String algoName) {
        try {
            Mac mac = Mac.getInstance(algoName);
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algoName);
            mac.init(key);

            String payload = Base64.getEncoder().encodeToString(inputText.getBytes());
            return new String(Hex.encodeHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))));

        } catch (Exception e) {
            throw new RuntimeException("cannot create " + algoName, e);
        }
    }

}
