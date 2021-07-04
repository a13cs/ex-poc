package ch.algotrader.ema.rest;

import ch.algotrader.ema.services.AccService;
import ch.algotrader.ema.strategy.SeriesService;
import ch.algotrader.ema.vo.AccInfoResponse;
import ch.algotrader.ema.vo.AccTradesResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Paths;
import java.util.List;

// todo: use sse/ws for online bars
//@ConditionalOnExpression(value = "${initFromCsv:true}")
//@ConditionalOnProperty(name = "initFromCsv", havingValue = "true")
@RestController
public class EmaRest {

    @Value("${barDuration}")
    private int barDuration;

    @Value("${initFromCsv}")
    private boolean initFromCsv;

    @Autowired
    SeriesService seriesService;

    @Autowired
    AccService accService;

    private static final String FILE_NAME = "bnc_trades_%ss.csv";

    private static final Logger logger = LoggerFactory.getLogger(EmaRest.class);

    // todo: use csv series only for back testing
    @RequestMapping(method = RequestMethod.GET, path = "/bars/{from}")
    public List<List<String>> latestBars(@PathVariable(value = "from") String from) {
        String fileName = String.format(FILE_NAME, barDuration);
        return seriesService.getLatestCSVBars(from, Paths.get(fileName));
    }

    // {indicator} = short / long (ema)
    @RequestMapping(method = RequestMethod.GET, path = "/indicator/{indicator}/{from}")
    public List<List<String>> latestIndicators(@PathVariable(value = "indicator") String indicator, @PathVariable(value = "from") String from) {
        String fileName = String.format(FILE_NAME, barDuration);
        return seriesService.getIndicator(indicator, from, Paths.get(fileName));
    }

    // todo: use from
    @RequestMapping(method = RequestMethod.GET, path = "/signals/{from}")
    public List<List<String>> latestSignals(@PathVariable(value = "from") String from) {
        return seriesService.getSignals(from);
    }

    @RequestMapping(method = RequestMethod.GET, path = "/acc")
    public AccInfoResponse accInfo() throws JsonProcessingException {
        return accService.getInfo();
    }

    @RequestMapping(method = RequestMethod.GET, path = "/myTrades")
    public List<AccTradesResponse> accTradesList() throws JsonProcessingException {
        return accService.getAccTradesList();
    }

}
