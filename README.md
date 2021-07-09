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
'http://localhost:8080/indicator/long/0'
###### entry/exit signals
'http://localhost:8080/signals/0'

###### Account
'http://localhost:8080/acc'
'http://localhost:8080/myTrades'


application.properties
======================
EMA
- emaPeriodShort=10
- emaPeriodLong=15

Series bars 
- barDuration = 10
- skippedFirstBars = 5

###### Default back-testing and offline usage flags: todo.