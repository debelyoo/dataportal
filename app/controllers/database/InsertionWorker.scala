package controllers.database

import akka.actor.Actor
import controllers.util.{DateFormatHelper, FiniteQueue, CoordinateHelper, Message, ApproxSwissProj}
import models.spatial._
import models.Sensor
import com.vividsolutions.jts.geom.Point
import scala.collection.immutable.Queue
import controllers.database.BatchManager._
import scala.Some

class InsertionWorker extends Actor {
  implicit def queue2finitequeue[A](q: Queue[A]) = new FiniteQueue[A](q)
  var logCache = Queue[String]()
  val LOG_CACHE_MAX_SIZE = 20

  def createUniqueString(str1: String, str2: String) = str1+":"+str2

  def receive = {
    case Message.SetInsertionBatch(batchId, dataType, lines, sensors) => {
      insertionBatches(batchId) = (lines, sensors)
      batchProgress(batchId) = (lines.length, 0)
      //val dataType = sensors.head.datatype
      DataLogManager.insertionBatchWorker ! Message.Work(batchId, dataType)
    }

    /*case Message.GetSpatializationProgress(batchId) => {
      val percentage = batchProgress.get(batchId).map {
        case (nbTot, nbDone) => math.round((nbDone.toDouble / nbTot.toDouble) * 100)
      }
      sender ! percentage
    }*/
    case Message.InsertTemperatureLog(batchId, ts, sensor, temperatureValue) => {
      try {
        // fetch the sensor in DB, to get its id
        val sensorInDb = Sensor.getByNameAndAddress(sensor.name, sensor.address)
        assert(sensorInDb.isDefined, {println("[Message.InsertTemperatureLog] Sensor is not in Database !")})
        //println("[RCV message] - insert temperature log: "+temperatureValue+", "+ sensorInDb.get)
        val uniqueString = createUniqueString(sensor.address, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        if (!logCache.contains(uniqueString)) {
          // insert log in cache only if cache does not contain unique string (address-timestamp)
          val tl = new TemperatureLog()
          tl.setSensor(sensorInDb.get)
          tl.setTimestamp(ts)
          tl.setValue(temperatureValue)
          val persisted = tl.save() // persist in DB
          if (persisted) {
            logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
            Some(true)
          } else None
        } else Some(false)
        BatchManager.updateBatchProgress(batchId, "Insertion")
        //sender ! res // return Option(true) if all ok, Option(false) if entry is already in DB, None if an error occurred
      } catch {
        case ae: AssertionError => None
      }
    }
    case Message.InsertCompassLog(batchId, ts, sensor, compassValue) => {
      try {
        val sensorInDb = Sensor.getByNameAndAddress(sensor.name, sensor.address)
        assert(sensorInDb.isDefined, {println("[Message.InsertCompassLog] Sensor is not in Database !")})
        //println("[RCV message] - insert compass log: "+compassValue+", "+ sensorInDb.get)
        val uniqueString = createUniqueString(sensor.address, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        val res = if (!logCache.contains(uniqueString)) {
          val cl = new CompassLog()
          cl.setSensor(sensorInDb.get)
          cl.setTimestamp(ts)
          cl.setValue(compassValue)
          val persisted = cl.save() // persist in DB
          if (persisted) {
            logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
            Some(true)
          } else None
        } else Some(false)
        BatchManager.updateBatchProgress(batchId, "Insertion")
        //sender ! res
      } catch {
        case ae: AssertionError => None
      }
    }
    case Message.InsertWindLog(batchId, ts, sensor, windValue) => {
      try {
        val sensorInDb = Sensor.getByNameAndAddress(sensor.name, sensor.address)
        assert(sensorInDb.isDefined, {println("[Message.InsertWindLog] Sensor is not in Database !")})
        //println("[RCV message] - insert wind log: "+ windValue +", "+ sensorInDb.get)
        val uniqueString = createUniqueString(sensor.address, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        val res = if (!logCache.contains(uniqueString)) {
          val wl = new WindLog()
          wl.setSensor(sensorInDb.get)
          wl.setTimestamp(ts)
          wl.setValue(windValue)
          val persisted = wl.save() // persist in DB
          if (persisted) {
            logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
            Some(true)
          } else None
        } else Some(false)
        BatchManager.updateBatchProgress(batchId, "Insertion")
        //sender ! res
      } catch {
        case ae: AssertionError => None
      }
    }
    //case Message.InsertGpsLog(ts, sensor, north, east) => {
    case Message.InsertGpsLog(batchId, ts, sensor, latitude, longitude) => {
      try {
        val sensorInDb = Sensor.getByNameAndAddress(sensor.name, sensor.address)
        assert(sensorInDb.isDefined, {println("[Message.InsertGpsLog] Sensor is not in Database !")})
        val uniqueString = createUniqueString(sensor.address, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        val res = if (!logCache.contains(uniqueString)) {
          val (lat, lon) = if (math.abs(latitude) > 90 || math.abs(longitude) > 180) {
            // east coordinate comes as longitude var, north coordinate comes as latitude var
            val arr = ApproxSwissProj.LV03toWGS84(longitude, latitude, 0L).toList
            //val latitude = arr(0)
            //val longitude = arr(1)
            (arr(0), arr(1))
          } else {
            (latitude, longitude)
          }
          //println("[RCV message] - insert gps log: "+ longitude +":"+ latitude +", "+ sensorInDb.get)
          val geom = CoordinateHelper.wktToGeometry("POINT("+ lon +" "+ lat +")")
          val gl = new GpsLog()
          gl.setSensor(sensorInDb.get)
          gl.setTimestamp(ts)
          gl.setGeoPos(geom.asInstanceOf[Point])
          val persisted = gl.save() // persist in DB
          if (persisted) {
            logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
            Some(true)
          } else None
        } else Some(false)
        BatchManager.updateBatchProgress(batchId, "Insertion")
        //sender ! res
      } catch {
        case ae: AssertionError => None
      }
    }
    case Message.InsertRadiometerLog(batchId, ts, sensor, radiometerValue) => {
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
            rl.setSensor(sensorInDb.get)
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
            tl.setSensor(sensorInDb.get)
            tl.setTimestamp(ts)
            tl.setValue(radiometerValue)
            val persisted = tl.save() // persist in DB
            if (persisted) {
              logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
              Some(true)
            } else None
          }
        } else Some(false)
        BatchManager.updateBatchProgress(batchId, "Insertion")
        //sender ! res
      } catch {
        case ae: AssertionError => sender ! None
      }
    }
    case Message.InsertSensor(sensor) => {
      //println("[RCV message] - insert sensor: "+sensor)
      sender ! sensor.save
    }
    case Message.SkipLog(batchId) => {
      val batchNumbers = batchProgress.get(batchId)
      if (batchNumbers.isDefined) {
        batchProgress(batchId) = (batchNumbers.get._1, batchNumbers.get._2 + 1)
        if (batchNumbers.get._1 == batchNumbers.get._2 + 1) println("Import batch ["+ batchId +"]: 100%")
      }
    }
  }
}