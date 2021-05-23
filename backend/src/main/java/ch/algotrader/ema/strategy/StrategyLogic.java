package ch.algotrader.ema.strategy;

import ch.algotrader.ema.services.TradingService;
import ch.algotrader.ema.vo.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.ta4j.core.*;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;

@Component
public class StrategyLogic implements InitializingBean {
    
    private static final Logger logger = LoggerFactory.getLogger(StrategyLogic.class);

    @Value("${symbol}") private String symbol;
    @Value("${quantity}") private BigDecimal quantity;

    @Value("${emaPeriodShort}") private int emaPeriodShort;
    @Value("${emaPeriodLong}") private int emaPeriodLong;

    private final TradingService tradingService;
    private final BarSeries series;

    private DifferenceIndicator emaDifference;

    @Autowired
    public StrategyLogic(TradingService tradingService) {
        this.tradingService = tradingService;
        this.series = new BaseBarSeriesBuilder().withName("bnc_series").build();
    }

    @Override
    public void afterPropertiesSet() {
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(this.series);
        EMAIndicator emaShort = new EMAIndicator(closePriceIndicator, this.emaPeriodShort);
        EMAIndicator emaLong = new EMAIndicator(closePriceIndicator, this.emaPeriodLong);
        this.emaDifference = new DifferenceIndicator(emaShort, emaLong);
    }

    public void handleTradeEvent(TradeEvent event) {

        if (this.series.getEndIndex() >= 0) {
            synchronized (series) {
                series.addTrade(Math.abs(event.getAmount()), event.getPrice());
            }
        }
    }

    @Scheduled(cron = "*/10 * * * * *")
    public void onTime() {
        synchronized (series) {
            try {
                logBar();
                evaluateLogic();
                createNewBar();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void logBar() {
        
        int i = this.series.getEndIndex();
        if (i > 0 && i < emaPeriodLong) {
            Bar bar = this.series.getBar(i);
            logger.info("open {} high {} low {} close {} vol {} trades {}",
                    bar.getOpenPrice(),
                    bar.getHighPrice(),
                    bar.getLowPrice(),
                    bar.getClosePrice(),
                    bar.getVolume(),
                    bar.getTrades());
            
        } else if (i >= emaPeriodLong) {

            Bar bar = this.series.getBar(i);
            Num emaDiff = this.emaDifference.getValue(i);
            Num emaDiffPrev = this.emaDifference.getValue(i - 1);

            logger.info("open {} high {} low {} close {} vol {} trades {} emaDiffPrev {} emaDiff {}",
                    bar.getOpenPrice(),
                    bar.getHighPrice(),
                    bar.getLowPrice(),
                    bar.getClosePrice(),
                    bar.getVolume(),
                    bar.getTrades(),
                    emaDiffPrev,
                    emaDiff);
        }
    }

    private void evaluateLogic() {

        Strategy s = buildStrategy(series);

        int i = this.series.getEndIndex();
        if (i >= emaPeriodLong) {
/*
            Num emaDiff = this.emaDifference.getValue(i);
            Num emaDiffPrev = this.emaDifference.getValue(i - 1);

            if (emaDiff.doubleValue() > 0 && emaDiffPrev.doubleValue() <= 0) {

                logger.info("!!!!!!!! BUY !!!!!!!!!)");
                tradingService.sendOrder("buy", quantity, symbol);

            } else if (emaDiff.doubleValue() < 0 && emaDiffPrev.doubleValue() >= 0) {

                logger.info("!!!!!!!! SELL !!!!!!!!!");
                tradingService.sendOrder("sell", quantity, symbol);
            }*/

            if(s.shouldEnter(i)) {
                // buy
                logger.info("!!!!!!!! BUY !!!!!!!!!)");
                tradingService.sendOrder("buy", quantity, symbol);
            } else if(s.shouldExit(i)) {
                //sell or close
                logger.info("!!!!!!!! SELL !!!!!!!!!");
                tradingService.sendOrder("sell", quantity, symbol);
            }
        }
    }

    public static Strategy buildStrategy(BarSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // The bias is bullish when the shorter-moving average moves above the longer
        // moving average.
        // The bias is bearish when the shorter-moving average moves below the longer
        // moving average.
        EMAIndicator shortEma = new EMAIndicator(closePrice, 9);
        EMAIndicator longEma = new EMAIndicator(closePrice, 26);

        StochasticOscillatorKIndicator stochasticOscillK = new StochasticOscillatorKIndicator(series, 14);

        MACDIndicator macd = new MACDIndicator(closePrice, 9, 26);
        EMAIndicator emaMacd = new EMAIndicator(macd, 18);

        // Entry rule
        Rule entryRule = new OverIndicatorRule(shortEma, longEma) // Trend
                .and(new CrossedDownIndicatorRule(stochasticOscillK, 20)) // Signal 1
                .and(new OverIndicatorRule(macd, emaMacd)); // Signal 2

        // Exit rule
        Rule exitRule = new UnderIndicatorRule(shortEma, longEma) // Trend
                .and(new CrossedUpIndicatorRule(stochasticOscillK, 20)) // Signal 1
                .and(new UnderIndicatorRule(macd, emaMacd)); // Signal 2

        return new BaseStrategy(entryRule, exitRule);
    }

    private void createNewBar() {
    
        // create new bar
        ZonedDateTime now = ZonedDateTime.now();
        Duration duration = Duration.ofSeconds(10);
        Bar newBar = new BaseBar(duration, now, this.series.function());
    
        // set price to closing price of previous bar
        int i = this.series.getEndIndex();
        if (i >= 0) {
            Bar previousBar = series.getBar(i);
            newBar.addPrice(previousBar.getClosePrice());
        }
    
        series.addBar(newBar);
    }

}
