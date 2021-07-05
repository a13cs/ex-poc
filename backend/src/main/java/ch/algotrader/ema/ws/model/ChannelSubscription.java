package ch.algotrader.ema.ws.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Builder
@Data
public class ChannelSubscription {

    private String method;
    private List<String> params;
    private Integer id;

    public static ChannelSubscription trades(String pair) {
        return ChannelSubscription.builder()
                .method("SUBSCRIBE")
                .params(Arrays.asList(
//                        pair + "@aggTrade",
                        pair + "@trade",
                        pair + "@depth"))
                .id(new Random(Instant.now().toEpochMilli()).nextInt(100))
                .build();
    }

    public static ChannelSubscription list() {
        return ChannelSubscription.builder()
                .method("LIST_SUBSCRIPTIONS")
                .id(new Random(Instant.now().toEpochMilli()).nextInt(100))
                .build();
    }

}
