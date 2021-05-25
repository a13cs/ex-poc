package ch.algotrader.ema.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.READ;

@RestController
public class EmaRest {

    Logger logger = LoggerFactory.getLogger(EmaRest.class);

    @RequestMapping(method = RequestMethod.GET, path = "/bars/{index}")
    public List<List<String>> latestBars(@PathVariable(value = "index") String index) throws IOException {

        String fileName = "bnc_trades_" + 5 + "s.csv";
        Path path = Paths.get(fileName);
        BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(path, READ));
        if(Files.exists(path)) {
            int size = bis.available();
            byte[] buffer = new byte[size];

            if (bis.read(buffer) > 0) {
                BufferedReader bufferedReader =
                        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buffer)));

                List<String> header = Arrays.asList(bufferedReader.readLine().split(","));
                List<List<String>> latestBars =
                        bufferedReader.lines()
                        .skip(Long.parseLong(index) - 1)
//                        .sorted(Comparator.reverseOrder())
                        .map(line -> Arrays.asList(line.split(","))).collect(Collectors.toList());

                latestBars.add(0, header);
                return latestBars;
            }
        }
        return Collections.emptyList();
    }

}
