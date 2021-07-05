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

  balance = {};

  constructor(private http: HttpClient) {
  }

  ngOnInit(): void {

    // create charts

    const chart = createChart(document.body, {
      width: 800,
      height: 900,
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
        text: 'EMA Cross',
        fontSize: 24,
        horzAlign: 'left',
        vertAlign: 'bottom',
      },
    });

    const series = chart.addCandlestickSeries();

    // long
    let smaLineFirst = chart.addLineSeries({
      color: 'rgb(10,22,125)',
      lineWidth: 3,
    });

    let smaLineSecond = chart.addLineSeries({
      color: 'rgb(4,107,232)',
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
        color: 'rgba(2,28,6,0.4)',
        visible: true,
        text: 'Close Price Line Chart',
        fontSize: 24,
        horzAlign: 'left',
        vertAlign: 'bottom',
      },
    });

    const closeLine = chartLine.addLineSeries({
      color: 'rgb(17,10,151)',
      lineWidth: 2,
    });

    //  ===============================================================================

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
            // let p : number = (Math.round(point[4] * 1000) / 1000)//.toFixed(2);
            lineData.push({time: +point[1] as UTCTimestamp, value: point[4] | 0})
          }
        })
        console.log(data)
        series.setData(data);

        closeLine.setData(lineData)

        this.http.get<any[]>('/be/indicator/long/0').subscribe(
          // this.http.get<any[]>('/indicator/long').subscribe(
          d => {
            console.log(d)
            d = d.map(i => i[1] )

            let indicatorData: any[] = []
            for (let i = 1; i < data.length; i++) {
              indicatorData.push({time: +data[i].time /*startTime*/ as UTCTimestamp, value: +d[i] | data[i].close})
            }
            // d.forEach(point => {
            //   indicatorData.push({time: +point[0] as UTCTimestamp, value: +point[0] | 0})
            // } )
            smaLineFirst.setData(indicatorData)
          }
        );

        this.http.get<any[]>('/be/indicator/short/0').subscribe(
          // this.http.get<any[]>('/indicator/short').subscribe(
          d => {
            console.log(d)
            d = d.map(i => i[1] )

            let indicatorData: any[] = []
            for (let i = 1; i < data.length; i++) {
              indicatorData.push({time: +data[i].time /*startTime*/ as UTCTimestamp, value: +d[i] | data[i].close})
            }
            smaLineSecond.setData(indicatorData)
          }
        );

      })

    this.http.get<any[]>('/be/signals/0').subscribe( d => {
    // this.http.get<any[]>('/signals/0').subscribe( d => {
      console.log(d)

      let signals: any[] = d.map(s => {
        return {
          time:  +s[0] as UTCTimestamp,
          color: s[1] === 'B' ? 'rgb(7,130,19)' : 'rgb(113,10,11)',
          position: 'aboveBar',
          shape: s[1] === 'B' ? "arrowUp" : "arrowDown",
          text: s[1] || 'X'
        }
      })

      series.setMarkers(signals)
    })


/*  // todo
    this.http.get<any[]>('/be/acc').subscribe(
    // this.http.get<any[]>('/acc').subscribe(
      d => {
        // let data : any = Object.keys(d)
        console.log("BTC SPOT Balance: " + JSON.stringify(d))
        this.balance = d;
      }
    )
*/


  }

}
