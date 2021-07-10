import {Component, OnInit} from '@angular/core';
import {createChart, CrosshairMode, ISeriesApi, UTCTimestamp} from 'lightweight-charts';
import {HttpClient} from "@angular/common/http";

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {

  proxyConf = true

  title = 'app';
  indicatorVisible = false

  smaLineSecond: any
  smaLineFirst: any

  closeLine: any

  csvTimestamp: any
  data: any[] = []
  series: any = []

  balance = {};

  lastBar: any;

  constructor(private http: HttpClient) {
  }

  showIndicator(show: boolean, emaLength: string /*short/long*/){
    this.indicatorVisible = show
    if(!show) {
      (<ISeriesApi<"Line">>this.smaLineSecond).setData([])
    } else {
      let prefix = this.proxyConf ? '/be' : ''
      this.http.get<any[]>(prefix + '/indicator/' + emaLength + '/0/'+this.csvTimestamp).subscribe(
        d => {
          console.log(d)

          let indicatorData: any[] = []
          for (let i = 1; i < this.data.length; i++) {
            indicatorData.push({time: +this.data[i].time /*startTime*/ as UTCTimestamp, value: +d[i] | this.data[i].close})
          }
          this.smaLineSecond.setData(indicatorData)
        }
      );
    }
  }

  updateLastBar() {

  }

  ngOnInit(): void {

    // create charts

    const chart = createChart(document.body, {
      width: 600,
      height: 700,
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

    this.series = chart.addCandlestickSeries();

    // long
    this.smaLineFirst = chart.addLineSeries({
      color: 'rgb(10,22,125)',
      lineWidth: 3,
    });

    this.smaLineSecond = chart.addLineSeries({
      color: 'rgb(4,107,232)',
      lineWidth: 2,
    });

    // todo: add volume
    // chart.addHistogramSeries()

    // let volumeSeries = chart.addHistogramSeries({
    //   color: '#26a69a',
    //   priceFormat: {
    //     type: 'volume',
    //   },
    //   priceScaleId: '',
    //   scaleMargins: {
    //     top: 0.8,
    //     bottom: 0,
    //   },
    // });
    // {time, value, color}


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

    // TODO: add trade quantity dots graph
    this.closeLine = chartLine.addLineSeries({
      color: 'rgb(17,10,151)',
      lineWidth: 2,
    });

    //  ===============================================================================

    // load data
    let prefix = this.proxyConf ? '/be' : ''

    this.getData()

    this.http.get<any[]>(prefix + '/signals/0').subscribe( d => {
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

      this.series.setMarkers(signals)
    })


/*
    // this.http.get<any[]>(prefix + '/acc').subscribe(
    this.http.get<any[]>('/acc').subscribe(
      d => {
        // let data : any = Object.keys(d)
        console.log("BTC SPOT Balance: " + JSON.stringify(d))
        this.balance = d;
      }
    )
*/

    setInterval(() => this.http.get<any[]>(prefix + '/lastTrade').subscribe(
      d => {
        let price = d[0]
        let barDuration = d[1]
        // console.log("Price, Bar Duration: " + d)

        // let now = this.data[this.data.length-1].time + barDuration
        let now = new Date().getTime() / 1000

        // console.log(now)
        // console.log(this.lastBar.time)

        let endBarDiff = Math.round(now - this.lastBar.time );
        console.log("EndBarDiff: " + endBarDiff);

        // TODO: bar duration < 30 sec
        if(endBarDiff > barDuration) {
          this.getData()
          this.lastBar = this.data.pop()
          console.log(this.lastBar);

          // todo: update() with new bar
        }

        (<ISeriesApi<"Candlestick">>this.series).update({
            time: this.lastBar.time,
            open: this.lastBar.open,
            high: +price > this.lastBar.high ? +price : this.lastBar.high,
            low: +price < this.lastBar.low ? +price : this.lastBar.low,
            close: +price
          })
      }), 1_500)

  }

  getData() {
    let prefix = this.proxyConf ? '/be' : ''

    this.http.get<any[]>(prefix + '/bars/0').subscribe(
      d => {
        let lineData: any[] = []
        // console.log("bars: " + JSON.stringify(d))

        this.csvTimestamp = d.slice(0,1)[0];
        console.log("csvTimestamp: " + this.csvTimestamp)

        d.slice(1).forEach( point => {
          if(+point[1]) {
            this.data.push({
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
        this.lastBar = this.data[this.data.length-1]
        // console.log("lastBar: " + JSON.stringify(this.lastBar))

        this.series.setData(this.data);
        this.closeLine.setData(lineData)
      })
  }

}
