package ch.algotrader.ema.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AccTradesResponse {

    private String symbol;// "BNBBTC",
    private Integer id;// 28457,
    private Integer orderId;// 100234,
    private Integer orderListId;// -1, //Unless OCO, the value will always be -1
    private Double price;// "4.00000100",
    private Double qty;// "12.00000000",
    private Double quoteQty;// "48.000012",
    private Double commission;// "10.10000000",
    private String commissionAsset;// "BNB",
    private Long time;// 1499865549590,
    private String displayTime;// 1499865549590,
    private Boolean isBuyer;// true,
    private Boolean isMaker;// false,
    private Boolean isBestMatch;// true

}
