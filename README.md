trade-poc
=========
######testnet.binance.vision


##### BE:
`mvn clean install`
`cd backend && java -jar target/demo-be-0.0.1-SNAPSHOT.jar`

#####FE:
`npm start`

#### REST:

###### Bars
'http://localhost:8080/bars/{fromIndex}'

###### Strategy
'http://localhost:8080/indicator/long/{fromIndex}/{csvTimestamp}' 
###### entry/exit signals
'http://localhost:8080/signals/{fromIndex}'

###### Account
'http://localhost:8080/acc'
'http://localhost:8080/myTrades'


application.properties
======================
EMA
- emaPeriodShort=10
- emaPeriodLong=15

Series bars 
- barDuration(sec)
- skippedFirstBars

###### Default back-testing and offline usage flags: todo.
- 'offline' cli param/app props, to not subscribe during back testing
