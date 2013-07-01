package controllers.database

import models.spatial.GpsLog
import models.Sensor
import controllers.util.SensorLog
import scala.collection.mutable.{Map => MMap}

object BatchManager {
  val spatializationBatches = MMap[String, (List[GpsLog], List[Sensor], List[SensorLog])]() // batchId -> (nbElementsTotal, nbElementsDone)
  val insertionBatches = MMap[String, (Array[String], Map[String, Sensor])]() // batchId -> (Array of lines, sensors)
  val batchProgress = MMap[String, (Int, Int)]()

  def updateBatchProgress(batchId: String, batchType: String) {
    val batchNumbers = batchProgress.get(batchId)
    batchProgress(batchId) = (batchNumbers.get._1, batchNumbers.get._2 + 1)
    if (batchNumbers.get._1 == batchNumbers.get._2 + 1) println(batchType + " batch ["+ batchId +"]: 100%")
  }
}
