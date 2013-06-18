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

trait ResponseFormatter {
  def logsAsJson(logList: List[JsonSerializable]): JsValue = {
    val jsList = Json.toJson(logList.map(log => Json.parse(log.toJson)))
    // build return JSON obj with array and count
    Json.toJson(Map("logs" -> jsList, "count" -> Json.toJson(logList.length)))
  }

  def logsAsKml(logList: List[KmlSerializable]): NodeSeq = {
    val xmlList = logList.map(log => log.toKml)
    val kmlStr = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><xml xmlns=\"http://www.opengis.net/xml/2.2\">"+ xmlList.mkString("") +"</xml>"
    val xmlDoc = XML.fromString(kmlStr)
    //xmlDoc.setXmlVersion("1.0")
    //println(xmlDoc.getDoctype)

    asXml(xmlDoc)
    //kmlStr

  }

  /**
   * Create a GML document with a list of data logs
   * @param logList The list of logs to put in the GML document
   * @return An XML document
   */
  def logsAsGml(logList: List[GmlSerializable]): NodeSeq = {
    val xmlList = logList.map(log => log.toGml)
    val gmlStr = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
      "<wfs:FeatureCollection xmlns=\"http://www.opengis.net/wfs\" xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:gml=\"http://www.opengis.net/gml\" xmlns:ecol=\"http://ecol.epfl.ch\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
      "<gml:boundedBy><gml:null>unknown</gml:null></gml:boundedBy>" +
      "" + xmlList.mkString("") +
      "</wfs:FeatureCollection>"
    val xmlDoc = XML.fromString(gmlStr)

    asXml(xmlDoc)
  }

  private def asXml(dom: org.w3c.dom.Node): Node = {
    /*val dom2sax = new DOM2SAX(dom)
    val adapter = new NoBindingFactoryAdapter
    dom2sax.setContentHandler(adapter)
    dom2sax.parse()
    adapter.rootElem*/
    //
    val transformerFactory = javax.xml.transform.TransformerFactory.newInstance()
    val source = new DOMSource(dom)
    val adapter = new NoBindingFactoryAdapter
    val saxResult = new SAXResult(adapter)
    val transformer = transformerFactory.newTransformer()
    transformer.transform(source, saxResult)
    adapter.rootElem
  }
}
