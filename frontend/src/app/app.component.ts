import {Component, OnInit} from '@angular/core';
import {createChart, CrosshairMode, UTCTimestamp} from 'lightweight-charts';
import {HttpClient} from "@angular/common/http";

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  title = 'app';

  constructor(private http: HttpClient) {
  }

  ngOnInit(): void {
    const chart = createChart(document.body, {
      width: 500,
      height: 400,
      timeScale: {
        timeVisible: true,
        secondsVisible: false,
      },
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
    // points[0]["time"] = '2019-04-11'
    points[0]["time"] = 1622211820

    points[1] = [];
    points[1]["open"] = 2;
    points[1]["high"] = 3;
    points[1]["low"] = 2;
    points[1]["close"] = 3;
    points[1]["time"] = 1622221820

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

    const chartLine = createChart(document.body, {
      width: 600,
      height: 300,
      layout: {
        backgroundColor: '#ffffff',
        textColor: 'rgba(33, 56, 77, 1)',
      },
      grid: {
        vertLines: {
          color: 'rgba(197, 203, 206, 0.7)',
        },
        horzLines: {
          color: 'rgba(197, 203, 206, 0.7)',
        },
      },
      timeScale: {
        timeVisible: true,
        secondsVisible: true,
        // fixLeftEdge: true
      },
    });

    let smaLine = chartLine.addLineSeries({
      color: 'rgba(4, 111, 232, 1)',
      lineWidth: 2,
    });

    // let data: any[] = [
    //   {time: 1622211820, value: 1.0},
    //   {time: 1622222825, value: 2.5},
    //   {time: 1622233830, value: 3.0},
    //   {time: 1622244835, value: 4.5},
    //   {time: 1622255840, value: 5.0},
    //   {time: 1622266845, value: 6.5},
    //   {time: 1622277850, value: 7.0},
    //   {time: 1622288855, value: 8.5},
    // ];
    // smaLine.setData(data);

    // this.http.get<any[]>('assets/ema_time_close.json').subscribe(
    this.http.get<any[]>('/be/indicator/a/0').subscribe(
      d => {
        console.log(d)
        let data: any[] = []
        d.forEach(point => {
          data.push({time: +point[0] as UTCTimestamp, value: +point[1]})
        } )
        smaLine.setData(data)
      }
    );

  }

}
