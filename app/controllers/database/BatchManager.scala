package controllers.database

import scala.collection.mutable.{Map => MMap}
import models.Device

object BatchManager {
  /**
   * A map containing the current insertion batches | batchId -> (filename, Array of lines, sensors)
   */
  val insertionBatches = MMap[String, (Array[String], Map[String, Device])]()
  /**
   * A map containing the progress of the insertion batches | batchId -> (filename/datatype, nbElementsTotal, nbElementsDone)
   */
  val batchProgress = MMap[String, (String, Int, Int)]()

  /**
   * Get the insertion progress of a specific batch
   * @param batchId The batch id
   * @return The progress as a percentage, and a hint (indicates the type of data that has been inserted)
   */
  def insertionProgress(batchId: String): Option[(String, Long)] = {
    val percentage = batchProgress.get(batchId).map {
      case (hint, nbTot, nbDone) => (hint, math.floor((nbDone.toDouble / nbTot.toDouble) * 100).toLong)
    }
    if(percentage.isDefined && percentage.get._2 == 100L) {
      cleanCompletedBatch(batchId)
    }
    percentage
  }

  /**
   * Update the progress of a batch
   * @param batchId The id of the batch
   * @param batchType The type of data in this batch
   */
  def updateBatchProgress(batchId: String, batchType: String) {
    val batchNumbers = batchProgress.get(batchId)
    batchProgress(batchId) = (batchNumbers.get._1, batchNumbers.get._2, batchNumbers.get._3 + 1)
    if (batchNumbers.get._2 == batchNumbers.get._3 + 1) {
      println(batchType + " batch ["+ batchId +"]: 100%")
    }
  }

  /**
   * Remove batch from lists when done
   * @param batchId The id of the batch
   */
  def cleanCompletedBatch(batchId: String) {
    batchProgress.remove(batchId)
    //println("batch ["+ batchId +"] removed from progress map")
    if (insertionBatches.contains(batchId)) {
      insertionBatches.remove(batchId)
      //println("batch ["+ batchId +"] removed from insertionBatches map")
    }
  }
}
