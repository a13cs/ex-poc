package ch.algotrader.ema.services;

import ch.algotrader.ema.strategy.StrategyLogic;
import ch.algotrader.ema.vo.Subscription;
import ch.algotrader.ema.vo.TradeEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;

@Service
@ClientEndpoint
public class MarketDataService implements DisposableBean, InitializingBean {

    private static final Logger LOGGER = LogManager.getLogger(MarketDataService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final StrategyLogic strategyLogic;

    @Value("${ws-uri}")
    private String wsUrl;

    private Session session;

    @Autowired
    public MarketDataService(StrategyLogic strategyLogic) {
        this.strategyLogic = strategyLogic;
    }

    public void subscribeTrades(String topic) {

        try {
            if (this.session == null || ! this.session.isOpen()) {
                this.session = initSession();
            }

            final String ser = new ObjectMapper().writeValueAsString(Subscription.trades(topic));
            LOGGER.info("sending " + ser);
            this.session.getBasicRemote().sendText(ser);
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
            final JsonNode json = objectMapper.readTree(msg);
            if (json.isArray()) {
                final JsonNode typeNode = json.get(1);
                if ("tu".equals(typeNode.asText())) {
                    final TradeEvent tradeEvent = TradeEvent.fromJson(json);
                    strategyLogic.handleTradeEvent(tradeEvent);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
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
            ssn = container.connectToServer(this, URI.create(wsUrl));

            LOGGER.info("session open: " + ssn.isOpen());

        } catch (DeploymentException | IOException e) {
            LOGGER.error(e);
        }
        return ssn;
    }

}
