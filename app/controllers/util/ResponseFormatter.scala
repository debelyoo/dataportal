package controllers.util

import play.api.libs.json.{Json, JsValue}
import controllers.util.json.JsonSerializable
import controllers.util.kml.KmlSerializable
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
    val kmlStr = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><kml xmlns=\"http://www.opengis.net/kml/2.2\">"+ xmlList.mkString("") +"</kml>"
    val xmlDoc = XML.fromString(kmlStr)
    //xmlDoc.setXmlVersion("1.0")
    //println(xmlDoc.getDoctype)

    asXml(xmlDoc)
    //kmlStr

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
