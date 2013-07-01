package controllers.database

import akka.actor.Actor
import controllers.util.{DataImporter, Message}
import controllers.database.BatchManager._
import models.spatial.DataLogManager


class SpatializationBatchWorker extends Actor {
  val MARGIN_IN_SEC = 1

  def receive = {
    case Message.Work(batchId, dataType) => {
      spatializationBatches.get(batchId).map { case (gLogs, sensors, logs) =>
        for {
          gl <- gLogs
          sensor <- sensors
        } {
          val tl = DataLogManager.getClosestLog(logs, gl.getTimestamp, MARGIN_IN_SEC, sensor.id)
          if (tl.isDefined) {
            dataType match {
              case DataImporter.Types.TEMPERATURE => DataLogManager.spatializationWorker ! Message.SpatializeTemperatureLog(batchId, gl, tl.get)
              case DataImporter.Types.WIND => DataLogManager.spatializationWorker ! Message.SpatializeWindLog(batchId, gl, tl.get)
              case DataImporter.Types.RADIOMETER => DataLogManager.spatializationWorker ! Message.SpatializeRadiometerLog(batchId, gl, tl.get)
            }
          } else {
            // if no close log has been found, increment the progress counter anyway (log has been processed)
            DataLogManager.spatializationWorker ! Message.NoCloseLog(batchId)
          }
        }
      }
    }
  }

}
