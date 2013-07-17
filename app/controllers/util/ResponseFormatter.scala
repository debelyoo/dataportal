package controllers.util

import play.api.libs.json.{Json, JsValue}
import controllers.util.json.JsonSerializable
import controllers.util.xml.{GmlSerializable, KmlSerializable}
import play.libs.XML
import scala.xml.{Node, NodeSeq}
import com.sun.org.apache.xalan.internal.xsltc.trax.DOM2SAX
import scala.xml.parsing.NoBindingFactoryAdapter
import javax.xml.transform.sax.SAXResult
import javax.xml.transform.dom.DOMSource
import play.api.mvc.Results.Ok
import java.util.Date
import play.api.Logger

trait ResponseFormatter {

  val JSON_FORMAT = "json"
  val KML_FORMAT = "xml"
  val GML_FORMAT = "gml"

  /**
   * Format the logs list in the right format, depending on what has been requested in the REST query
   * @param format The desired format
   * @param logMap The list of logs (by sensor)
   * @return A HTTP response
   */
  def formatResponse(format: String, logMap: Map[String, List[WebSerializable]]) = {
    format match {
      case JSON_FORMAT => Ok(logsAsJson(logMap))
      //case KML_FORMAT => Ok(logsAsKml(logMap))
      case GML_FORMAT => Ok(logsAsGml(logMap))
      case _ => Ok(logsAsJson(logMap))
    }
  }

  /**
   * Format the list of logs as JSON array
   * @param logMap A map with the logs for each sensor
   * @return A JSON object with the sensor logs
   */
  private def logsAsJson(logMap: Map[String, List[JsonSerializable]]): JsValue = {
    val jsObjectList = logMap.map { case (sensorName, logList) => {
      val jsList = Json.toJson(logList.map(log => Json.parse(log.toJson)))
      val sensorAndValues = Json.toJson(Map("sensor" -> Json.toJson(sensorName), "values" -> jsList, "count" -> Json.toJson(logList.length)))
      sensorAndValues
    }}
    // build return JSON obj with array
    Json.toJson(Map("logs" -> Json.toJson(jsObjectList)))
  }

  /*private def logsAsKml(logList: List[KmlSerializable]): NodeSeq = {
    val xmlList = logList.map(log => log.toKml)
    val kmlStr = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><xml xmlns=\"http://www.opengis.net/xml/2.2\">"+ xmlList.mkString("") +"</xml>"
    val xmlDoc = XML.fromString(kmlStr)
    //xmlDoc.setXmlVersion("1.0")
    //println(xmlDoc.getDoctype)

    asXml(xmlDoc)
    //kmlStr
  }*/

  /**
   * Create a GML document with a list of data logs
   * @param logMap The list of logs to put in the GML document
   * @return An XML document
   */
  private def logsAsGml(logMap: Map[String, List[GmlSerializable]]): NodeSeq = {
    val start = new Date
    if (logMap.size > 1) Logger.warn("logsAsGml() - logMap contains more than one log serie")
    val logList = logMap.head._2 // If multiple sensors are sent, take only the first serie
    val xmlList = logList.map(log => log.toGml)
    val gmlStr = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
      "<wfs:FeatureCollection xmlns=\"http://www.opengis.net/wfs\" xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:gml=\"http://www.opengis.net/gml\" xmlns:ecol=\"http://ecol.epfl.ch\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
      "<gml:boundedBy><gml:null>unknown</gml:null></gml:boundedBy>" +
      "" + xmlList.mkString("") +
      "</wfs:FeatureCollection>"
    val xmlDoc = XML.fromString(gmlStr)
    val diff = (new Date).getTime - start.getTime
    //Logger.info("logsAsGml() - GML formatting done ! ["+ diff +"ms]")
    asXml(xmlDoc)
  }

  /**
   * Convert W3C XML format to Scala XML
   * @param dom The XML document in W3C XML format
   * @return The document as Scala XML
   */
  private def asXml(dom: org.w3c.dom.Node): Node = {
    val transformerFactory = javax.xml.transform.TransformerFactory.newInstance()
    val source = new DOMSource(dom)
    val adapter = new NoBindingFactoryAdapter
    val saxResult = new SAXResult(adapter)
    val transformer = transformerFactory.newTransformer()
    transformer.transform(source, saxResult)
    adapter.rootElem
  }
}
