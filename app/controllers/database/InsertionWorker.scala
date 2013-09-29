package controllers.database

import akka.actor.Actor
import controllers.util.{DateFormatHelper, FiniteQueue, CoordinateHelper, Message, ApproxSwissProj}
import models.spatial._
import models._
import com.vividsolutions.jts.geom.{Geometry, Point}
import scala.collection.immutable.Queue
import controllers.database.BatchManager._
import scala.Some
import java.util.Date
import play.api.Logger
import models.elemoImport.{TemperatureLogCat}
import scala.Some

class InsertionWorker extends Actor {
  implicit def queue2finitequeue[A](q: Queue[A]) = new FiniteQueue[A](q)
  var logCache = Queue[String]()
  var lastSpeedPoint: Option[GpsLog] = None // most recent point for which the instantaneous speed has been calculated
  val LOG_CACHE_MAX_SIZE = 20

  def createUniqueString(str1: String, str2: String) = str1+":"+str2

  def receive = {
    case Message.SetInsertionBatch(batchId, filename, dataType, lines, sensors) => {
      insertionBatches(batchId) = (lines, sensors)
      batchProgress(batchId) = (filename, lines.length, 0)
      //val dataType = sensors.head.datatype
      DataLogManager.insertionBatchWorker ! Message.Work(batchId, dataType)
    }
    case Message.InsertTemperatureLog(batchId, ts, sensor, temperatureValue) => {
      try {
        // fetch the sensor in DB, to get its id
        val sensorInDb = Device.getByNameAndAddress(sensor.name, sensor.address)
        assert(sensorInDb.isDefined, {println("[Message.InsertTemperatureLog] Sensor is not in Database !")})
        //println("[RCV message] - insert temperature log: "+temperatureValue+", "+ sensorInDb.get)
        val uniqueString = createUniqueString(sensor.address, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        if (!logCache.contains(uniqueString)) {
          // insert log in cache only if cache does not contain unique string (address-timestamp)
          val tl = new TemperatureLog()
          tl.setDevice(sensorInDb.get)
          val m = DataLogManager.getById[Mission](8) // hard coded - warning
          tl.setMission(m.get)
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
        val sensorInDb = Device.getByNameAndAddress(sensor.name, sensor.address)
        assert(sensorInDb.isDefined, {println("[Message.InsertCompassLog] Sensor is not in Database !")})
        //println("[RCV message] - insert compass log: "+compassValue+", "+ sensorInDb.get)
        val uniqueString = createUniqueString(sensor.address, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        val res = if (!logCache.contains(uniqueString)) {
          val cl = new CompassLog()
          cl.setDevice(sensorInDb.get)
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
        val sensorInDb = Device.getByNameAndAddress(sensor.name, sensor.address)
        assert(sensorInDb.isDefined, {println("[Message.InsertWindLog] Sensor is not in Database !")})
        //println("[RCV message] - insert wind log: "+ windValue +", "+ sensorInDb.get)
        val uniqueString = createUniqueString(sensor.address, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        val res = if (!logCache.contains(uniqueString)) {
          val wl = new WindLog()
          wl.setDevice(sensorInDb.get)
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
    /*case Message.InsertGpsLog(batchId, ts, setNumber, sensor, latitude, longitude) => {
      try {
        //println("[Message.InsertGpsLog] "+sensor.address)
        val sensorInDb = Device.getByNameAndAddress(sensor.name, sensor.address)
        assert(sensorInDb.isDefined, {println("[Message.InsertGpsLog] Sensor is not in Database !")})
        // ALTERNATIVE - create unique string, use timestamp format with seconds (and not milli) --> import only one GPS point per second
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
          val speed = computeSpeed(ts, geom, setNumber)
          val gl = new GpsLog()
          gl.setSensor(sensorInDb.get)
          gl.setTimestamp(ts)
          gl.setGeoPos(geom.asInstanceOf[Point])
          gl.setSpeed(speed)
          gl.setSetNumber(setNumber)
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
    }*/
    case Message.InsertRadiometerLog(batchId, ts, sensor, radiometerValue) => {
      try {
        val sensorInDb = Device.getByNameAndAddress(sensor.name, sensor.address)
        assert(sensorInDb.isDefined, {println("[Message.InsertRadiometerLog] Sensor is not in Database !")})
        //println("QUEUE -> "+logCache)
        val uniqueString = createUniqueString(sensor.address, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        if (!logCache.contains(uniqueString)) {
          // if value > 50 -> radiometer sensor, else temperature sensor - TODO change this
          if (sensorInDb.get.name.contains("Pyrgeometer") || sensorInDb.get.name.contains("Pyranometer")) {
            //println("[RCV message] - insert radiometer log: "+ radiometerValue)
            val rl = new RadiometerLog()
            rl.setDevice(sensorInDb.get)
            rl.setTimestamp(ts)
            rl.setValue(radiometerValue)
            val persisted = rl.save() // persist in DB
            if (persisted) {
              logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
              Some(true)
            } else None
          } else {
            //println("[RCV message] - insert temperature log: "+ radiometerValue)
            if (sensorInDb.get.datatype != "temperature") {
              sensorInDb.get.updateType("temperature") // update the type of the sensor (the PT100 of the radiometer)
            }
            val tl = new TemperatureLog()
            tl.setDevice(sensorInDb.get)
            tl.setTimestamp(ts)
            tl.setValue(radiometerValue)
            val persisted = tl.save() // persist in DB
            if (persisted) {
              logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
              Some(true)
            } else None
          }
        }
        BatchManager.updateBatchProgress(batchId, "Insertion")
        //sender ! res
      } catch {
        case ae: AssertionError => sender ! None
      }
    }
    /// TEST
    case Message.InsertGpsLog(batchId, ts, setNumber, sensor, latitude, longitude) => {
      try {
        //println("[Message.InsertGpsLog] "+sensor.address)
        val sensorInDb = Device.getByNameAndAddress(sensor.name, sensor.address)
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
          val gl = new TrajectoryPoint()
          gl.setTimestamp(ts)
          gl.setCoordinate(geom.asInstanceOf[Point])
          val m = DataLogManager.getById[Mission](8) // hard coded - warning
          gl.setMission(m.get)
          val persisted = gl.save() // persist in DB
          if (persisted) {
            logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
            Some(true)
          } else None
        } else Some(false)
        BatchManager.updateBatchProgress(batchId, "Insertion")
      } catch {
        case ae: AssertionError => None
      }
    }
    case Message.InsertDevice(sensor) => {
      //println("[RCV message] - insert sensor: "+sensor)
      sender ! sensor.save
    }
    case Message.SkipLog(batchId) => {
      val batchNumbers = batchProgress.get(batchId)
      if (batchNumbers.isDefined) {
        batchProgress(batchId) = (batchNumbers.get._1, batchNumbers.get._2, batchNumbers.get._3 + 1)
        if (batchNumbers.get._2 == batchNumbers.get._3 + 1) println("Import batch ["+ batchId +"]: 100%")
      }
    }
  }

  /**
   * Compute the instantaneous speed in one GPS point
   * @param ts The timestamp of the GPS point
   * @param geom The GPS point
   * @param setNumber The set number of the GPS point
   * @return The speed in m/s
   */
  private def computeSpeed(ts: Date, geom: Geometry, setNumber: Int): Double = {
    val INTERVAL_BETWEEN_SPEED_POINTS = 1000L // milliseconds (it means that we compute speed every 1000 ms)
    val timeDiff = lastSpeedPoint.map(gpsLog => ts.getTime - gpsLog.getTimestamp.getTime) // time difference (ms) between gps points
    val lastSetNumber = lastSpeedPoint.map(gpsLog => gpsLog.getSetNumber)
    if (lastSetNumber.isDefined && lastSetNumber.get != setNumber) lastSpeedPoint = None // reset lastSpeedPoint if set number has changed
    val point = geom.asInstanceOf[Point]
    if (lastSpeedPoint.isEmpty) {
      val speed = 0.0
      val gl = new GpsLog()
      gl.setTimestamp(ts)
      gl.setGeoPos(point)
      gl.setSpeed(speed)
      gl.setSetNumber(setNumber)
      lastSpeedPoint = Some(gl)
      speed
    } else if (lastSpeedPoint.isDefined && timeDiff.get >= INTERVAL_BETWEEN_SPEED_POINTS) {
      // speed needs to be recomputed for this point
      val distance = computeDistanceBetween2GpsPoints(lastSpeedPoint.get.getGeoPos.getX, lastSpeedPoint.get.getGeoPos.getY, point.getX, point.getY)
      val speed = distance / (timeDiff.get.toDouble / 1000.0) // return m/s
      //Logger.info("TimeDiff: "+ timeDiff +" ms, Distance: "+distance+" m, speed: "+speed+" m/s")
      val gl = new GpsLog()
      gl.setTimestamp(ts)
      gl.setGeoPos(point)
      gl.setSpeed(speed)
      gl.setSetNumber(setNumber)
      lastSpeedPoint = Some(gl)
      speed
    } else {
      // return the speed that was computed in the last speed point
      lastSpeedPoint.map(_.getSpeed).get
    }
  }

  /**
   * Compute the distance between 2 GPS points (in meters)
   * @param lon1 Longitude of 1st point
   * @param lat1 Latitude of 1st point
   * @param lon2 Longitude of 2nd point
   * @param lat2 Latitude of 2nd point
   * @return The distance in meters
   */
  private def computeDistanceBetween2GpsPoints(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double = {
    val R = 6371 // km
    val dLat = math.toRadians(lat2 - lat1)
    val dLon = math.toRadians(lon2 - lon1)
    var lat1InRad = math.toRadians(lat1)
    var lat2InRad = math.toRadians(lat2)

    val a = math.sin(dLat/2) * math.sin(dLat/2) + math.sin(dLon/2) * math.sin(dLon/2) * math.cos(lat1InRad) * math.cos(lat2InRad)
    val c = 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))
    val distKm = R * c
    distKm * 1000 // return meters
  }
}