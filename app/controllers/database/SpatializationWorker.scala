package controllers.database

import akka.actor.{Actor, ActorSystem}
import controllers.util.{SensorLog, JPAUtil, Message}
import models.spatial._
import models.spatial.DataLogManager._
import javax.persistence.EntityManager
import BatchManager._

class SpatializationWorker extends Actor {

  def receive = {
    case Message.SetSpatializationBatch(batchId, gpsLogs, sensors, sensorLogs) => {
      spatializationBatches(batchId) = (gpsLogs, sensors, sensorLogs)
      val dataType = sensors.head.datatype
      //batchProgress(batchId) = (dataType, gpsLogs.length * sensors.length, 0)
      batchProgress(batchId) = (dataType, sensorLogs.length, 0)
      DataLogManager.spatializationBatchWorker ! Message.Work(batchId, dataType)
    }

    case Message.SpatializeTemperatureLog(batchId, gpsLog, sensorLog) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        val batchNumbers = batchProgress.get(batchId)
        assert(batchNumbers.isDefined, {println("[ASSERTION] batchId is not in batch map")})
        if (sensorLog.asInstanceOf[TemperatureLog].getGpsLog == null) {
          em.getTransaction.begin()
          // add position in temperaturelog table
          //updateGeoPos[TemperatureLogCat](sensorLog.getId.longValue(), gpsLog.getGeoPos, em)
          linkSensorLogToGpsLog[TemperatureLog](sensorLog.getId.longValue(), gpsLog.getId.longValue(), em)
          em.getTransaction.commit()
        }
        // update batch progress, even if sensor log is already linked to a GPS point
        BatchManager.updateBatchProgress(batchId, "Spatialization")
      } catch {
        case ae: AssertionError =>
        case ex: Exception => ex.printStackTrace()
      } finally {
        em.close()
      }
    }
    case Message.SpatializeWindLog(batchId, gpsLog, sensorLog) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        val batchNumbers = batchProgress.get(batchId)
        assert(batchNumbers.isDefined, {println("[ASSERTION] batchId is not in batch map")})
        if (sensorLog.asInstanceOf[WindLog].getGpsLog == null) {
          em.getTransaction.begin()
          // add position in windlog table
          //updateGeoPos[WindLog](sensorLog.getId.longValue(), gpsLog.getGeoPos, em)
          linkSensorLogToGpsLog[WindLog](sensorLog.getId.longValue(), gpsLog.getId.longValue(), em)
          em.getTransaction.commit()
        }
        BatchManager.updateBatchProgress(batchId, "Spatialization")
      } catch {
        case ae: AssertionError =>
        case ex: Exception => ex.printStackTrace()
      } finally {
        em.close()
      }
    }
    case Message.SpatializeRadiometerLog(batchId, gpsLog, sensorLog) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        val batchNumbers = batchProgress.get(batchId)
        assert(batchNumbers.isDefined, {println("[ASSERTION] batchId is not in batch map")})
        if (sensorLog.asInstanceOf[RadiometerLog].getGpsLog == null) {
          em.getTransaction.begin()
          // add position in radiometerlog table
          //updateGeoPos[RadiometerLog](sensorLog.getId.longValue(), gpsLog.getGeoPos, em)
          linkSensorLogToGpsLog[RadiometerLog](sensorLog.getId.longValue(), gpsLog.getId.longValue(), em)
          em.getTransaction.commit()
        }
        BatchManager.updateBatchProgress(batchId, "Spatialization")
      } catch {
        case ae: AssertionError =>
        case ex: Exception =>
      } finally {
        em.close()
      }
    }
    case Message.SpatializeCompassLog(batchId, gpsLog, sensorLog) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        val batchNumbers = batchProgress.get(batchId)
        assert(batchNumbers.isDefined, {println("[ASSERTION] batchId is not in batch map")})
        if (sensorLog.asInstanceOf[CompassLog].getGpsLog == null) {
          em.getTransaction.begin()
          // add position in compasslog table
          //updateGeoPos[CompassLog](sensorLog.getId.longValue(), gpsLog.getGeoPos, em)
          linkSensorLogToGpsLog[CompassLog](sensorLog.getId.longValue(), gpsLog.getId.longValue(), em)
          em.getTransaction.commit()
        }
        BatchManager.updateBatchProgress(batchId, "Spatialization")
      } catch {
        case ae: AssertionError =>
        case ex: Exception =>
      } finally {
        em.close()
      }
    }
    case Message.NoCloseLog(batchId) => {
      val batchNumbers = batchProgress.get(batchId)
      if (batchNumbers.isDefined) {
        BatchManager.updateBatchProgress(batchId, "Spatialization")
      }
    }

    case _ => println("[SpatializationWorker] Unknown message")
  }

}
