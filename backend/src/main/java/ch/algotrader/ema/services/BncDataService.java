package ch.algotrader.ema.services;

import ch.algotrader.ema.ws.JSONTextDecoder;
import ch.algotrader.ema.ws.JSONTextEncoder;
import org.springframework.messaging.handler.annotation.MessageMapping;

import javax.websocket.OnMessage;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint(value = "/", decoders = {JSONTextDecoder.class}, encoders = {JSONTextEncoder.class})
public class BncDataService {



}
