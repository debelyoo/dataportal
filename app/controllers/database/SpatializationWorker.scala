package controllers.database

import akka.actor.{Actor, ActorSystem}
import controllers.util.{SensorLog, JPAUtil, Message}
import models.spatial._
import models._
import DataLogManager._
import javax.persistence.EntityManager
import BatchManager._
import models.{RadiometerLog, WindLog, CompassLog, TemperatureLog}

class SpatializationWorker extends Actor {

  def receive = {
    case Message.SetSpatializationBatch(batchId, gpsLogs, sensors, sensorLogs) => {
      spatializationBatches(batchId) = (gpsLogs, sensors, sensorLogs)
      val dataType = sensors.head.datatype
      //batchProgress(batchId) = (dataType, gpsLogs.length * sensors.length, 0)
      batchProgress(batchId) = (dataType, sensorLogs.length, 0)
      DataLogManager.spatializationBatchWorker ! Message.Work(batchId, dataType, 0L)
    }

    case _ => println("[SpatializationWorker] Unknown message")
  }

}
