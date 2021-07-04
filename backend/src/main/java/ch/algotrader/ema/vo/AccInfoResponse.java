package ch.algotrader.ema.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
public class AccInfoResponse {

//      GET /api/v3/account (HMAC SHA256)
//      param: timestamp LONG
/*    {
            "makerCommission": 15,
            "takerCommission": 15,
            "buyerCommission": 0,
            "sellerCommission": 0,
            "canTrade": true,
            "canWithdraw": true,
            "canDeposit": true,
            "updateTime": 123456789,
            "accountType": "SPOT",
            "balances": [
        {
            "asset": "BTC",
            "free": "4723846.89208129",
            "locked": "0.00000000"
        },
        {
            "asset": "LTC",
            "free": "4763368.68006011",
            "locked": "0.00000000"
        }
  ],
        "permissions": [
        "SPOT"
  ]
    }*/

    private int makerCommission;
    private int takerCommission;
    private int buyerCommission;
    private int sellerCommission;
    private boolean canTrade;
    private boolean canWithdraw;
    private boolean canDeposit;
    private  long updateTime;
    private String accountType;

    private List<Balance> balances;
    private List<String> permissions;

    @Data
    static class Balance {
        String asset, free, locked;
    }

}
