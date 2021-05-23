package ch.algotrader.ema.ws;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;

public class JSONTextDecoder implements Decoder.Text<JsonObject> {

    private final Gson gson = new Gson();

    @Override
    public JsonObject decode(String s) {
        return gson.toJsonTree(s).getAsJsonObject();
    }

    @Override
    public boolean willDecode(String s) {
        JsonObject jsonObject = gson.toJsonTree(s).getAsJsonObject();
        return jsonObject != null && jsonObject.isJsonObject();
    }

    @Override
    public void init(EndpointConfig config) {
    }

    @Override
    public void destroy() {
    }
}