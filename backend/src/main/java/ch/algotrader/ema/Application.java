package ch.algotrader.ema;

import ch.algotrader.ema.services.MarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

@SpringBootApplication
@EnableScheduling
public class Application implements CommandLineRunner {

    private static ConfigurableApplicationContext context;
    private final MarketDataService marketDataService;


    @Autowired
    public Application(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    public static void main(String[] args) {
        context = SpringApplication.run(Application.class, args);
    }

    @Bean
    public ConfigurableApplicationContext getContext() {
        return context;
    }
    @Bean
    public BarSeries getSeries() {
        return new BaseBarSeriesBuilder().withName("bnc_series").build();
    }


    @Override
    public void run(String... args) {
        marketDataService.subscribeTrades("btcusdt");
    }
}
