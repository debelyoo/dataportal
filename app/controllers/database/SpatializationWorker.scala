package controllers.database

import akka.actor.{Actor, ActorSystem}
import controllers.util.{SensorLog, JPAUtil, Message}
import models.spatial._
import models.spatial.DataLogManager._
import javax.persistence.EntityManager
import models.mapping.{MapGpsRadiometer, MapGpsWind, MapGpsTemperature}
import BatchManager._

class SpatializationWorker extends Actor {

  def receive = {
    case Message.SetSpatializationBatch(batchId, gpsLogs, sensors, sensorLogs) => {
      spatializationBatches(batchId) = (gpsLogs, sensors, sensorLogs)
      batchProgress(batchId) = (gpsLogs.length * sensors.length, 0)
      val dataType = sensors.head.datatype
      DataLogManager.spatializationBatchWorker ! Message.Work(batchId, dataType)
    }

    /*case Message.GetSpatializationProgress(batchId) => {
      val percentage = batchProgress.get(batchId).map {
        case (nbTot, nbDone) => math.round((nbDone.toDouble / nbTot.toDouble) * 100)
      }
      sender ! percentage
    }*/

    case Message.SpatializeTemperatureLog(batchId, gpsLog, sensorLog) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        val batchNumbers = batchProgress.get(batchId)
        if (batchNumbers.isDefined) {
          em.getTransaction.begin()
          // add position in temperaturelog table
          //updateGeoPos[TemperatureLog](sensorLog.getId.longValue(), gpsLog.getGeoPos, em)
          // create mapping gpslog <-> temperaturelog
          //MapGpsTemperature(gpsLog.getId, sensorLog.getId).save(em)
          linkSensorLogToGpsLog[TemperatureLog](sensorLog.getId.longValue(), gpsLog.getId.longValue(), em)
          em.getTransaction.commit()
          batchProgress(batchId) = (batchNumbers.get._1, batchNumbers.get._2 + 1)
          if (batchNumbers.get._1 == batchNumbers.get._2 + 1) println("Spatialization batch ["+ batchId +"]: 100%")
        }
      } catch {
        case ex: Exception =>
      } finally {
        em.close()
      }
    }
    case Message.SpatializeWindLog(batchId, gpsLog, sensorLog) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        val batchNumbers = batchProgress.get(batchId)
        if (batchNumbers.isDefined) {
          em.getTransaction.begin()
          // add position in windlog table
          //updateGeoPos[WindLog](sensorLog.getId.longValue(), gpsLog.getGeoPos, em)
          // create mapping gpslog <-> windlog
          //MapGpsWind(gpsLog.getId, sensorLog.getId).save(em)
          linkSensorLogToGpsLog[WindLog](sensorLog.getId.longValue(), gpsLog.getId.longValue(), em)
          em.getTransaction.commit()
          batchProgress(batchId) = (batchNumbers.get._1, batchNumbers.get._2 + 1)
          if (batchNumbers.get._1 == batchNumbers.get._2 + 1) println("Spatialization batch ["+ batchId +"]: 100%")
        }
      } catch {
        case ex: Exception => ex.printStackTrace()
      } finally {
        em.close()
      }
    }
    case Message.SpatializeRadiometerLog(batchId, gpsLog, sensorLog) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        val batchNumbers = batchProgress.get(batchId)
        if (batchNumbers.isDefined) {
          em.getTransaction.begin()
          // add position in radiometerlog table
          //updateGeoPos[RadiometerLog](sensorLog.getId.longValue(), gpsLog.getGeoPos, em)
          // create mapping gpslog <-> radiometerlog
          //MapGpsRadiometer(gpsLog.getId, sensorLog.getId).save(em)
          linkSensorLogToGpsLog[RadiometerLog](sensorLog.getId.longValue(), gpsLog.getId.longValue(), em)
          em.getTransaction.commit()
          batchProgress(batchId) = (batchNumbers.get._1, batchNumbers.get._2 + 1)
          if (batchNumbers.get._1 == batchNumbers.get._2 + 1) println("Spatialization batch ["+ batchId +"]: 100%")
        }
      } catch {
        case ex: Exception =>
      } finally {
        em.close()
      }
    }
    case Message.SpatializeCompassLog(batchId, gpsLog, sensorLog) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        val batchNumbers = batchProgress.get(batchId)
        if (batchNumbers.isDefined) {
          em.getTransaction.begin()
          // add position in compasslog table
          //updateGeoPos[CompassLog](sensorLog.getId.longValue(), gpsLog.getGeoPos, em)
          // create mapping gpslog <-> compasslog
          //MapGpsRadiometer(gpsLog.getId, sensorLog.getId).save(em)
          linkSensorLogToGpsLog[CompassLog](sensorLog.getId.longValue(), gpsLog.getId.longValue(), em)
          em.getTransaction.commit()
          batchProgress(batchId) = (batchNumbers.get._1, batchNumbers.get._2 + 1)
          if (batchNumbers.get._1 == batchNumbers.get._2 + 1) println("Spatialization batch ["+ batchId +"]: 100%")
        }
      } catch {
        case ex: Exception =>
      } finally {
        em.close()
      }
    }
    case Message.NoCloseLog(batchId) => {
      val batchNumbers = batchProgress.get(batchId)
      if (batchNumbers.isDefined) {
        batchProgress(batchId) = (batchNumbers.get._1, batchNumbers.get._2 + 1)
        if (batchNumbers.get._1 == batchNumbers.get._2 + 1) println("Spatialization batch ["+ batchId +"]: 100%")
      }
    }

    case _ => println("[SpatializationWorker] Unknown message")
  }

}
