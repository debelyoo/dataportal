package controllers.database

import akka.actor.Actor
import controllers.util.{DataImporter, Message}
import controllers.database.BatchManager._
import controllers.modelmanager.DataLogManager


class SpatializationBatchWorker extends Actor {
  val MARGIN_IN_SEC = 1

  def receive = {
    /**
     * Process: iterate over the sensor logs. For each sensor log, find the closest GPS log (if it exists), and link it to the sensor log
     */
    case Message.Work(batchId, dataType, missionId) => {
      //println("receive Message.Work")
      /*spatializationBatches.get(batchId).map { case (gLogs, sensors, logs) =>
        for {
          tl <- logs
        } {
          //println("gl: "+gl+", sensor: "+sensor)
          //val tl = DataLogManager.getClosestLog(logs, gl.getTimestamp, MARGIN_IN_SEC, sensor.id)
          val gl = DataLogManager.getClosestGpsLog(gLogs, tl.getTimestamp, MARGIN_IN_SEC)
          if (gl.isDefined) {
            dataType match {
              case DataImporter.Types.TEMPERATURE => DataLogManager.spatializationWorker ! Message.SpatializeTemperatureLog(batchId, gl.get, tl)
              case DataImporter.Types.WIND => DataLogManager.spatializationWorker ! Message.SpatializeWindLog(batchId, gl.get, tl)
              case DataImporter.Types.RADIOMETER => DataLogManager.spatializationWorker ! Message.SpatializeRadiometerLog(batchId, gl.get, tl)
            }
          } else {
            // if no close log has been found, increment the progress counter anyway (log has been processed)
            DataLogManager.spatializationWorker ! Message.NoCloseLog(batchId)
          }
        }
      }*/
    }
  }

}
