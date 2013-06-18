package controllers.database

import akka.actor.Actor
import controllers.util.{DateFormatHelper, FiniteQueue, CoordinateHelper, Message, ApproxSwissProj}
import models.spatial._
import models.Sensor
import com.vividsolutions.jts.geom.Point
import scala.collection.immutable.Queue

class InsertWorker extends Actor {
  implicit def queue2finitequeue[A](q: Queue[A]) = new FiniteQueue[A](q)
  var logCache = Queue[String]()
  val LOG_CACHE_MAX_SIZE = 20

  def createUniqueString(str1: String, str2: String) = str1+":"+str2

  def receive = {
    case Message.InsertTemperatureLog(ts, sensor, temperatureValue) => {
      try {
        // fetch the sensor in DB, to get its id
        val sensorInDb = Sensor.getByNameAndAddress(sensor.name, sensor.address)
        assert(sensorInDb.isDefined, {println("[Message.InsertTemperatureLog] Sensor is not in Database !")})
        //println("[RCV message] - insert temperature log: "+temperatureValue+", "+ sensorInDb.get)
        val uniqueString = createUniqueString(sensor.address, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        val res = if (!logCache.contains(uniqueString)) {
          // insert log in cache only if cache does not contain unique string (address-timestamp)
          val tl = new TemperatureLog()
          tl.setSensorId(sensorInDb.get.id)
          tl.setTimestamp(ts)
          tl.setValue(temperatureValue)
          val persisted = tl.save() // persist in DB
          if (persisted) {
            logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
            Some(true)
          } else None
        } else Some(false)
        sender ! res // return Option(true) if all ok, Option(false) if entry is already in DB, None if an error occurred
      } catch {
        case ae: AssertionError => None
      }
    }
    case Message.InsertCompassLog(ts, sensor, compassValue) => {
      try {
        val sensorInDb = Sensor.getByNameAndAddress(sensor.name, sensor.address)
        assert(sensorInDb.isDefined, {println("[Message.InsertCompassLog] Sensor is not in Database !")})
        //println("[RCV message] - insert compass log: "+compassValue+", "+ sensorInDb.get)
        val uniqueString = createUniqueString(sensor.address, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        val res = if (!logCache.contains(uniqueString)) {
          val cl = new CompassLog()
          cl.setSensorId(sensorInDb.get.id)
          cl.setTimestamp(ts)
          cl.setValue(compassValue)
          val persisted = cl.save() // persist in DB
          if (persisted) {
            logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
            Some(true)
          } else None
        } else Some(false)
        sender ! res
      } catch {
        case ae: AssertionError => None
      }
    }
    case Message.InsertWindLog(ts, sensor, windValue) => {
      try {
        val sensorInDb = Sensor.getByNameAndAddress(sensor.name, sensor.address)
        assert(sensorInDb.isDefined, {println("[Message.InsertWindLog] Sensor is not in Database !")})
        //println("[RCV message] - insert wind log: "+ windValue +", "+ sensorInDb.get)
        val uniqueString = createUniqueString(sensor.address, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        val res = if (!logCache.contains(uniqueString)) {
          val wl = new WindLog()
          wl.setSensorId(sensorInDb.get.id)
          wl.setTimestamp(ts)
          wl.setValue(windValue)
          val persisted = wl.save() // persist in DB
          if (persisted) {
            logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
            Some(true)
          } else None
        } else Some(false)
        sender ! res
      } catch {
        case ae: AssertionError => None
      }
    }
    case Message.InsertGpsLog(ts, sensor, north, east) => {
      try {
        val sensorInDb = Sensor.getByNameAndAddress(sensor.name, sensor.address)
        assert(sensorInDb.isDefined, {println("[Message.InsertGpsLog] Sensor is not in Database !")})
        val uniqueString = createUniqueString(sensor.address, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        val res = if (!logCache.contains(uniqueString)) {
          val arr = ApproxSwissProj.LV03toWGS84(east, north, 0L).toList
          val latitude = arr(0)
          val longitude = arr(1)
          //println("[RCV message] - insert gps log: "+ longitude +":"+ latitude +", "+ sensorInDb.get)
          val geom = CoordinateHelper.wktToGeometry("POINT("+ longitude +" "+ latitude +")")
          val gl = new GpsLog()
          gl.setSensorId(sensorInDb.get.id)
          gl.setTimestamp(ts)
          gl.setGeoPos(geom.asInstanceOf[Point])
          val persisted = gl.save() // persist in DB
          if (persisted) {
            logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
            Some(true)
          } else None
        } else Some(false)
        sender ! res
      } catch {
        case ae: AssertionError => None
      }
    }
    case Message.InsertRadiometerLog(ts, sensor, radiometerValue) => {
      try {
        val sensorInDb = Sensor.getByNameAndAddress(sensor.name, sensor.address)
        assert(sensorInDb.isDefined, {println("[Message.InsertRadiometerLog] Sensor is not in Database !")})
        //println("QUEUE -> "+logCache)
        val uniqueString = createUniqueString(sensor.address, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        val res = if (!logCache.contains(uniqueString)) {
          // if value is Int -> radiometer sensor, if Double -> temperature sensor
          //val intVal = DataLogManager.doubleToInt(radiometerValue)
          if (radiometerValue.isValidInt) {
            //println("[RCV message] - insert radiometer log: "+ radiometerValue.toInt +", "+ sensorInDb.get)
            val rl = new RadiometerLog()
            rl.setSensorId(sensorInDb.get.id)
            rl.setTimestamp(ts)
            rl.setValue(radiometerValue.toInt)
            val persisted = rl.save() // persist in DB
            if (persisted) {
              logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
              Some(true)
            } else None
          } else {
            //println("[RCV message] - insert temperature log: "+ intVal.get)
            if (sensorInDb.get.datatype != "temperature") {
              sensorInDb.get.updateType("temperature") // update the type of the sensor (the PT100 of the radiometer)
            }
            val tl = new TemperatureLog()
            tl.setSensorId(sensorInDb.get.id)
            tl.setTimestamp(ts)
            tl.setValue(radiometerValue)
            val persisted = tl.save() // persist in DB
            if (persisted) {
              logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
              Some(true)
            } else None
          }
        } else Some(false)
        sender ! res
      } catch {
        case ae: AssertionError => sender ! None
      }
    }
    case Message.InsertSensor(sensor) => {
      //println("[RCV message] - insert sensor: "+sensor)
      sender ! sensor.save
    }
  }
}