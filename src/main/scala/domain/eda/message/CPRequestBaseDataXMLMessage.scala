package at.energydash
package domain.eda.message

import model.EbMsMessage
import model.enums.EbMsMessageType
import config.Config

import akka.util.ByteString
import scalaxb.Helper
import xmlprotocol.{AddressType, DocumentMode, ECNumber, Number01, PROD, RoutingAddress, RoutingHeader, SIMU}
import xmlprotocol.cprequest.{v01p12 => cp12}

import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}
import scala.util.Try
import scala.xml.{Elem, Node, XML}

case class CPRequestBaseData(message: EbMsMessage) extends EdaMessage {
  override def getVersion(version: Option[String] = None): EdaXMLMessage[_] = CPRequestBaseDataXMLMessage(message)
}

case class CPRequestBaseDataXMLMessage(message: EbMsMessage) extends EdaXMLMessage[cp12.CPRequest] {
  override def rootNodeLabel: Some[String] = Some("CPRequest")

  override def schemaLocation: Option[String] = Some("http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12 " +
    "http://www.ebutilities.at/schemata/customerprocesses/MD_REQ_GN/03.12/ANFORDERUNG_GN")

  def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    import java.util.GregorianCalendar

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processCalendar = new GregorianCalendar(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    val dateFmt = new SimpleDateFormat("yyyy-MM-dd")

    val doc = cp12.CPRequest(
      cp12.MarketParticipantDirectory(
        RoutingHeader(
          RoutingAddress(message.sender, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          RoutingAddress(message.receiver, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          Helper.toCalendar(calendar)
        ),
        Number01,
        message.messageCode.toString,
        Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentMode](Config.interfaceMode match {
            case "SIMU" => SIMU
            case _ => PROD
          })),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[cp12.SchemaVersion](cp12.Number01u4612)),
        )
      ),
      cp12.ProcessDirectory(
        message.messageId.get,
        message.conversationId,
        Helper.toCalendar(dateFmt.format(processCalendar.getTime)),
        message.meter.map(x=>x.meteringPoint).getOrElse(""),
        Some(cp12.Extension(None, None, None, None, None, None, None, None, None, AssumptionOfCosts = false)),
      )
    )

    rewriteRootSchema(scalaxb.toXML[cp12.CPRequest](doc, Some("http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12"), Some("CPRequest"),
      scalaxb.toScope(
        Some("cp") -> "http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12",
        Some("ct") -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
        Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance",
      ),
      false).head, "CPRequest",
      "http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12 CPRequest_01p12.xsd")

  }

  override def toByte: Try[ByteString] = Try {
    val xml = toXML

    val xmlString = new StringWriter()
    XML.write(xmlString, xml, "UTF-8", true, null)

    ByteString.fromString(xmlString.toString)
  }
}

object CPRequestBaseDataXMLMessage extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[CPRequestBaseData] = {
    Try(scalaxb.fromXML[cp12.CPRequest](xmlFile)).map(document =>
      CPRequestBaseData(
        EbMsMessage(
          messageId = Some(document.ProcessDirectory.MessageId),
          conversationId = document.ProcessDirectory.ConversationId,
          sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
          receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
          messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
          messageCodeVersion = Some("01.00"),
        )
      )
    )
  }
}
