package ch.algotrader.ema.rest;

import ch.algotrader.ema.strategy.SeriesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

@RestController
public class EmaRest {

    @Value("${barDuration}")
    private int barDuration;

    @Autowired
    SeriesService seriesService;

    private static final String FILE_NAME = "bnc_trades_%ss.csv";

    private static final Logger logger = LoggerFactory.getLogger(EmaRest.class);

    @RequestMapping(method = RequestMethod.GET, path = "/bars/{from}")
    public List<List<String>> latestBars(@PathVariable(value = "from") String from) throws IOException {
        String fileName = String.format(FILE_NAME, barDuration);
        return seriesService.getLatestCSVBars(from, Paths.get(fileName));
    }

    @RequestMapping(method = RequestMethod.GET, path = "/indicator/{name}/{from}")
    public List<List<String>> latestIndicators(@PathVariable(value = "name") String indicator, @PathVariable(value = "from") String from) throws IOException {
        String fileName = String.format(FILE_NAME, barDuration);
        return seriesService.getIndicator(indicator, from, Paths.get(fileName));
    }

    // todo
    @RequestMapping(method = RequestMethod.GET, path = "/bars/positions/{from}")
    public List<List<String>> latestPositions(@PathVariable(value = "from") String from) {
        return seriesService.getPositions(from, Paths.get(FILE_NAME));
    }

}
