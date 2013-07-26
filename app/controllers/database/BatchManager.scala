package controllers.database

import models.spatial.GpsLog
import models.Sensor
import controllers.util.SensorLog
import scala.collection.mutable.{Map => MMap}

object BatchManager {
  val spatializationBatches = MMap[String, (List[GpsLog], List[Sensor], List[SensorLog])]() // batchId -> (list of GPS logs, list of sensors, list of data logs)
  val insertionBatches = MMap[String, (Array[String], Map[String, Sensor])]() // batchId -> (filename, Array of lines, sensors)
  val batchProgress = MMap[String, (String, Int, Int)]() // batchId -> (filename/datatype, nbElementsTotal, nbElementsDone)

  def updateBatchProgress(batchId: String, batchType: String) {
    val batchNumbers = batchProgress.get(batchId)
    batchProgress(batchId) = (batchNumbers.get._1, batchNumbers.get._2, batchNumbers.get._3 + 1)
    if (batchNumbers.get._2 == batchNumbers.get._3 + 1) {
      println(batchType + " batch ["+ batchId +"]: 100%")
    }
  }

  /**
   * Remove batch from lists when done
   */
  def cleanCompletedBatch(batchId: String) {
    batchProgress.remove(batchId)
    //println("batch ["+ batchId +"] removed from progress map")
    if (insertionBatches.contains(batchId)) {
      insertionBatches.remove(batchId)
      //println("batch ["+ batchId +"] removed from insertionBatches map")
    }
    if (spatializationBatches.contains(batchId)) {
      spatializationBatches.remove(batchId)
      //println("batch ["+ batchId +"] removed from spatializationBatches map")
    }
  }
}
