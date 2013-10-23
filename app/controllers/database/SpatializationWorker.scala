package controllers.database

import akka.actor.{Actor, ActorSystem}
import controllers.util.{ISensorLog, JPAUtil, Message}

class SpatializationWorker extends Actor {

  def receive = {
    /*case Message.SetSpatializationBatch(batchId, gpsLogs, sensors, sensorLogs) => {
      spatializationBatches(batchId) = (gpsLogs, sensors, sensorLogs)
      val dataType = sensors.head.getDatatype
      //batchProgress(batchId) = (dataType, gpsLogs.length * sensors.length, 0)
      batchProgress(batchId) = (dataType, sensorLogs.length, 0)
      DataLogManager.spatializationBatchWorker ! Message.Work(batchId, dataType, 0L)
    }*/

    case _ => println("[SpatializationWorker] Unknown message")
  }

}
