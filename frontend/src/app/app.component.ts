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

    // create charts

    const chart = createChart(document.body, {
      width: 500,
      height: 400,
      timeScale: {
        // barSpacing: 4,
        timeVisible: true,
        secondsVisible: true,
      },
      crosshair: {
        mode: CrosshairMode.Normal,
      }
    });

    chart.applyOptions({
      watermark: {
        color: 'rgba(11, 94, 29, 0.4)',
        visible: true,
        text: 'EMA',
        fontSize: 24,
        horzAlign: 'left',
        vertAlign: 'bottom',
      },
    });

    const series = chart.addCandlestickSeries();

    let smaLineOnTop = chart.addLineSeries({
      color: 'rgba(4, 111, 232, 1)',
      lineWidth: 2,
    });

    // todo: add volume
    // chart.addHistogramSeries()

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

    chartLine.applyOptions({
      watermark: {
        color: 'rgba(11, 94, 29, 0.4)',
        visible: true,
        text: 'Close Price Line Chart',
        fontSize: 24,
        horzAlign: 'left',
        vertAlign: 'bottom',
      },
    });

    const closeLine = chartLine.addLineSeries({
      color: 'rgba(4, 111, 232, 1)',
      lineWidth: 2,
    });


    // load data

    // this.http.get<any[]>('assets/bars.json').subscribe(
    this.http.get<any[]>('/be/bars/0').subscribe(
      // this.http.get<any[]>('/bars/0').subscribe(
      d => {
        console.log(d.slice(1))
        console.log(d)
        let data: any[] = []
        let lineData: any[] = []

        d.slice(1).forEach( point => {
          if(+point[1]) {
            data.push({
                open: point[/*"openPrice"*/3] | 0,
                high: point[/*"highPrice"*/5] | 0,
                low: point[/*"lowPrice"*/6] | 0,
                close: point[/*"closePrice"*/4] | 0,
                time: +point[/*"endTime"*/1]
              })
            lineData.push({time: +point[1] as UTCTimestamp, value: +point[4] | 0})
          }
        })
        console.log(data)
        series.setData(data);

        // todo
        let time = data.length > 0 ? +data[1]['time'] as UTCTimestamp : Date.now() as UTCTimestamp;
        series.setMarkers([{
          time: time,
          color: 'rgba(11, 94, 29, 0.4)',
          position: 'aboveBar',
          shape: "arrowUp",
          text: 'B'
        }])

        closeLine.setData(lineData)
      })

    // todo: default be config (duration, ema length)

    // this.http.get<any[]>('assets/ema_time_close.json').subscribe(
    this.http.get<any[]>('/be/indicator/a/0').subscribe(
    // this.http.get<any[]>('/indicator/ema/0').subscribe(
      d => {
        console.log(d)
        let data: any[] = []
        d.forEach(point => {
          data.push({time: +point[0] as UTCTimestamp, value: +point[1] | 0})
        } )
        smaLineOnTop.setData(data)
      }
    );

  }

}
