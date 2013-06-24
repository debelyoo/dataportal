package controllers.database

import models.spatial.GpsLog
import models.Sensor
import controllers.util.SensorLog
import scala.collection.mutable.{Map => MMap}

object SpatializationBatchManager {
  val batches = MMap[String, (List[GpsLog], List[Sensor], List[SensorLog])]() // batchId -> (nbElementsTotal, nbElementsDone)
  val batchProgress = MMap[String, (Int, Int)]()
}
