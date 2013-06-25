package controllers.database

import akka.actor.Actor
import controllers.util.{DataImporter, Message}
import controllers.database.SpatializationBatchManager._
import models.spatial.DataLogManager


class SpatializationBatchWorker extends Actor {
  val MARGIN_IN_SEC = 1

  def receive = {
    case Message.Work(batchId, dataType) => {
      batches.get(batchId).map { case (gLogs, sensors, logs) =>
        for {
          gl <- gLogs
          sensor <- sensors
          tl <- DataLogManager.getClosestLog(logs, gl.getTimestamp, MARGIN_IN_SEC, sensor.id)
        } {
          dataType match {
            case DataImporter.Types.TEMPERATURE => DataLogManager.spatializationWorker ! Message.SpatializeTemperatureLog(batchId, gl, tl)
            case DataImporter.Types.WIND => DataLogManager.spatializationWorker ! Message.SpatializeWindLog(batchId, gl, tl)
            case DataImporter.Types.RADIOMETER => DataLogManager.spatializationWorker ! Message.SpatializeRadiometerLog(batchId, gl, tl)
          }
          /*val batchNumbers = batchProgress.get(batchId)
          batchProgress(batchId) = (batchNumbers.get._1, batchNumbers.get._2 + 1)
          if (batchNumbers.get._1 == batchNumbers.get._2 + 1) println("Spatialization batch ["+ batchId +"]: 100%")
          */
        }
      }
    }
  }

}
