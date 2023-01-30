package at.energydash
package domain.eda.message

import akka.util.ByteString
import at.energydash.model.EbMsMessage

import java.io.StringWriter
import java.text.SimpleDateFormat
import scala.util.Try
import scala.xml.transform.{RewriteRule, RuleTransformer}
import scala.xml.{Elem, MetaData, NamespaceBinding, Node, Null, PrefixedAttribute, TopScope, XML}

trait EdaMessage[EDAType] {

  val message: EbMsMessage
  def rootNodeLabel: Option[String] = None
  def schemaLocation: Option[String] = None

  implicit val edaType: EDAType = edaType

  val dateFmt = new SimpleDateFormat("yyyy-MM-dd")

  def toXML: Node
  def toByte: ByteString = {
    val xml = if (rootNodeLabel.isDefined && schemaLocation.isDefined) {
      rewriteRootSchema(toXML, rootNodeLabel.get, schemaLocation.get)
    } else {
      toXML
    }

    val xmlString = new StringWriter()

    XML.save(s"Portfolio.xml", xml, "UTF-8", true, null)
    XML.write(xmlString, xml, "UTF-8", true, null)

    ByteString.fromString(xmlString.toString)
  }

  def rewriteRootSchema(xml: Node, rootNodeLabel: String, schemaLocaction: String): Node = {
    val schemaLoc = new PrefixedAttribute("xsi", "schemaLocation", schemaLocaction, Null)

    xml.asInstanceOf[Elem] % schemaLoc

//    val setSchemaAndNamespaceRule = new NamespaceAndSchema(rootNodeLabel, schemaLoc)
//    new RuleTransformer(setSchemaAndNamespaceRule).transform(xml).head
  }
}

trait EdaResponseType {
  def fromXML(xmlFile: Elem): Try[EdaMessage[_]]
}


// new class that extends RewriteRule
class NamespaceAndSchema(rootLabel: String, attrs: MetaData) extends RewriteRule {
  // create a RewriteRule that sets this as the only namespace
  override def transform(n: Node): Seq[Node] = n match {

    // ultimately, it's just a matter of setting the scope & attributes
    // on a new copy of the xml node
    case e: Elem if(e.label == rootLabel) =>
      e.copy(attributes = e.attributes.append(attrs))
    case n =>
      n
  }
}