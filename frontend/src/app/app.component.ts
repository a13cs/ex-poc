import {Component, OnInit} from '@angular/core';
import { createChart, CrosshairMode } from 'lightweight-charts';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  title = 'app';

  ngOnInit(): void {
    const chart = createChart(document.body, {
      width: 500,
      height: 400,
      crosshair: {
        mode: CrosshairMode.Normal,
      }
    });
    const series = chart.addCandlestickSeries();

    let points: any[] = [];
    points[0] = [];
    points[0]["open"] = 1;
    points[0]["high"] = 2;
    points[0]["low"] = 1;
    points[0]["close"] = 2;
    points[0]["time"] = '2019-04-11'

    points[1] = [];
    points[1]["open"] = 2;
    points[1]["high"] = 3;
    points[1]["low"] = 2;
    points[1]["close"] = 3;
    points[1]["time"] = '2019-04-12'

    series.setData(points);
    // const lineSeries = chart.addLineSeries();
    // lineSeries.setData([
    //   { time: '2019-04-11', value: 80.01 },
    //   { time: '2019-04-12', value: 96.63 },
    //   { time: '2019-04-13', value: 76.64 },
    //   { time: '2019-04-14', value: 81.89 },
    //   { time: '2019-04-15', value: 74.43 },
    //   { time: '2019-04-16', value: 80.01 },
    //   { time: '2019-04-17', value: 96.63 },
    //   { time: '2019-04-18', value: 76.64 },
    //   { time: '2019-04-19', value: 81.89 },
    //   { time: '2019-04-20', value: 74.43 },
    // ]);


    let smaLine = chart.addLineSeries({
      color: 'rgba(4, 111, 232, 1)',
      lineWidth: 2,
    });
    smaLine.setData([{time: points[0]["time"], value: 2},{time: points[1]["time"], value: 3}]);

  }

}
