package ch.algotrader.ema.ws.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

@Builder
@Data
public class ChannelSubscription {

    private String method;
    private List<String> params;
    private Integer id;

//    private long timestamp;
//    private String signature;

    public static ChannelSubscription trades(String pair) {
        return ChannelSubscription.builder()
                .method("SUBSCRIBE")
                .params(Arrays.asList(
                        pair + "@aggTrade",
                        pair + "@depth"))
                .id(12)
//                .timestamp(Instant.now().toEpochMilli())
                .build();
    }

    public static ChannelSubscription list() {
        return ChannelSubscription.builder()
                .method("LIST_SUBSCRIPTIONS")
                .id(new Random(Instant.now().toEpochMilli()).nextInt(100))
                .build();
    }

}
