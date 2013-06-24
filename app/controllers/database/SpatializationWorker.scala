package controllers.database

import akka.actor.{Actor, ActorSystem}
import controllers.util.{SensorLog, JPAUtil, Message}
import models.spatial.{DataLogManager, GpsLog, TemperatureLog}
import models.spatial.DataLogManager._
import javax.persistence.EntityManager
import models.mapping.MapGpsTemperature
import SpatializationBatchManager._

class SpatializationWorker extends Actor {

  def receive = {
    case Message.SetSpatializationBatch(batchId, gpsLogs, sensors, sensorLogs) => {
      batches(batchId) = (gpsLogs, sensors, sensorLogs)
      batchProgress(batchId) = (gpsLogs.length * sensors.length, 0)
      DataLogManager.spatializationBatchWorker ! Message.Work(batchId)
    }

    case Message.GetSpatializationProgress(batchId) => {
      val percentage = batchProgress.get(batchId).map {
        case (nbTot, nbDone) => math.round((nbDone.toDouble / nbTot.toDouble) * 100)
      }
      sender ! percentage
    }

    case Message.SpatializeTemperatureLog(batchId, gpsLog, sensorLog) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        val batchNumbers = batchProgress.get(batchId)
        if (batchNumbers.isDefined) {
          em.getTransaction.begin()
          // add position in temperaturelog table
          updateGeoPos[TemperatureLog](sensorLog.getId.longValue(), gpsLog.getGeoPos, em)
          // create mapping gpslog <-> temperaturelog
          MapGpsTemperature(gpsLog.getId, sensorLog.getId).save(em)
          em.getTransaction.commit()
          batchProgress(batchId) = (batchNumbers.get._1, batchNumbers.get._2 + 1)
        }
      } catch {
        case ex: Exception =>
      } finally {
        em.close()
      }
    }

    case _ => println("[SpatializationWorker] Unknown message")
  }

}
