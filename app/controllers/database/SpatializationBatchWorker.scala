package controllers.database

import akka.actor.Actor
import controllers.util.Message
import controllers.database.SpatializationBatchManager._
import models.spatial.DataLogManager


class SpatializationBatchWorker extends Actor {
  val MARGIN_IN_SEC = 1

  def receive = {
    case Message.Work(batchId) => {
      batches.get(batchId).map { case (gLogs, sensors, logs) =>
        for {
          gl <- gLogs
          sensor <- sensors
          tl <- DataLogManager.getClosestLog(logs, gl.getTimestamp, MARGIN_IN_SEC, sensor.id)
        } {
          //spatializationWorker ! Message.SpatializeTemperatureLog(batchId, gl, tl)
          val batchNumbers = batchProgress.get(batchId)
          batchProgress(batchId) = (batchNumbers.get._1, batchNumbers.get._2 + 1)
        }
      }
    }

    case Message.Test(batchId, gpsLog, sensorLog) => {
      val batchNumbers = batchProgress.get(batchId)
      batchProgress(batchId) = (batchNumbers.get._1, batchNumbers.get._2 + 1)
    }
  }

}
