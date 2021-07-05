package ch.algotrader.ema.services;


import ch.algotrader.ema.strategy.SeriesService;
import ch.algotrader.ema.strategy.StrategyLogic;
import ch.algotrader.ema.ws.JSONTextDecoder;
import ch.algotrader.ema.ws.JSONTextEncoder;
import ch.algotrader.ema.ws.model.ChannelSubscription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.ta4j.core.BaseBarSeries;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.UnresolvedAddressException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Service
@ClientEndpoint(encoders = {JSONTextEncoder.class}, decoders = {JSONTextDecoder.class})
public class MarketDataService implements DisposableBean, InitializingBean {

    private static final Logger LOGGER = LogManager.getLogger(MarketDataService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    static {
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MAPPER.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
        MAPPER.configure(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS, true);

        MAPPER.configure(SerializationFeature.INDENT_OUTPUT, true);
        MAPPER.configure(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED, true);
    }

    private static final String FILE_NAME = "bnc_trades_%ss.csv";

    private String topic;

    @Value("${api-secret}") private String apiSecret;

    private final StrategyLogic strategyLogic;

    private final SeriesService seriesService;

    @Value("${ws-uri}")
    private String wsUrl;

    private Session session;

    @Autowired
    private ConfigurableApplicationContext context;

    @Value("${initFromCsv}")
    private boolean initCsv;

    @Value("${barDuration}")
    private int barDuration;
    private static final double MIN_QUANTITY_LIMIT = 0.005;


    @Autowired
    public MarketDataService(StrategyLogic strategyLogic, SeriesService seriesService) {
        this.strategyLogic = strategyLogic;
        this.seriesService = seriesService;
    }

    public void subscribeTrades(String topic) {

        if(isNotBlank(topic)) this.topic = topic;
        if (initCsv) {
            Path path = Paths.get(String.format(FILE_NAME, barDuration));
            BaseBarSeries csvSeries = seriesService.getCsvSeries("ema", "0", path);
            if(!csvSeries.isEmpty()) strategyLogic.loadBarsFromCsv(csvSeries);
        }

        try {
            if (this.session == null || !this.session.isOpen() ) {
                if (!initSession()) {
                    strategyLogic.setOffline(true);
                    return; // or retry
                }

                ChannelSubscription subscription = ChannelSubscription.trades(topic);
                final String sub = MAPPER.writeValueAsString(subscription);

                LOGGER.info("sending " + sub);
                this.session.getBasicRemote().sendText(sub);

//                 list active
                String list = MAPPER.writeValueAsString(ChannelSubscription.list());
                this.session.getBasicRemote().sendText(list);
                LOGGER.info("sending " + list);
            }
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
        try {
            HashMap<String, String> map = MAPPER.readValue( msg, new TypeReference<>(){} );

            if ("trade".equals(map.get("e"))) {
//                LOGGER.info("msg {}", msg);
                LOGGER.info(System.lineSeparator());

                double quantity = Math.abs(Double.parseDouble(map.get("q")));
                if(quantity < MIN_QUANTITY_LIMIT) {
                    LOGGER.warn("Skipped trade with low quantity {}, price {}", map.get("q"), map.get("p"));
                    return;
                }
                map.forEach((k,v) -> LOGGER.info("{}::{}",k,v));

//                strategyLogic.handleTradeEvent(AggTradeEvent.fromJson(map));  // or send map
                strategyLogic.handleTradeEvent(map);
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
        if (session != null) this.session.close();
    }

    @Override
    public void afterPropertiesSet() {
    }

    private boolean initSession() {
        Session ssn;
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        try {
//            ssn = container.connectToServer(this, URI.create(wsUrl + "btcusdt" + "@aggTrade"));
            ssn = container.connectToServer(this, URI.create(wsUrl + "btcusdt" + "@trade"));
            LOGGER.info("session open: " + ssn.isOpen());

            this.session = ssn;
            return true;
        } catch (DeploymentException | IOException | UnresolvedAddressException e) {
            LOGGER.warn("Could not initialize websocket session. ", e);
//            SpringApplication.exit(context);
        }
        return false;
    }

}
