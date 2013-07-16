package controllers.util

import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.io.{ParseException, WKTReader}

object CoordinateHelper {
  /**
   * Convert a point (String notation) into a Geometry object
   * @param wktPoint The point in String notation
   * @return The Geometry object
   */
  def wktToGeometry(wktPoint: String): Geometry = {
    val fromText = new WKTReader()
    var geom: Geometry = null
    try {
      geom = fromText.read(wktPoint)
    } catch {
      case pe: ParseException => throw new RuntimeException("Not a WKT string:" + wktPoint)
    }
    geom
  }
}
