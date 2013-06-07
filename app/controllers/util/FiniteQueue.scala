package controllers.util

import scala.collection.immutable

class FiniteQueue[A](q: immutable.Queue[A]) {

  def enqueueFinite[B >: A](elem: B, maxSize: Int): immutable.Queue[B] = {
    var ret = q.enqueue(elem)
    while (ret.size > maxSize) { ret = ret.dequeue._2 }
    ret
  }
}
