package at.energydash
package domain.eda.message

import model.enums.EbMsMessageType
import model.{EbMsMessage, ResponseData}
import config.Config

import scalaxb.Helper
import xmlprotocol.{AddressType, DocumentMode, ECNumber, Number01, PROD, RoutingAddress, RoutingHeader, SIMU}
import xmlprotocol.cmrevoke.{v01p00 => v00}

import java.util.{Calendar, Date}
import scala.util.Try
import scala.xml.{Elem, Node}

case class CMRevokeMessage(message: EbMsMessage) extends EdaMessage {
  override def getVersion(version: Option[String]): EdaXMLMessage[_] = CMRevokeXMLMessageV0100(message)
}

case class CMRevokeXMLMessageV0100(message: EbMsMessage) extends EdaXMLMessage[v00.CMRevoke] {
  override def rootNodeLabel: Some[String] = Some("CMRevoke")

  override def schemaLocation: Option[String] =
    Some("http://www.ebutilities.at/schemata/customerconsent/cmrevoke/01p00 " +
      "http://www.ebutilities.at/schemata/customerprocesses/CM_REV_IMP/01.00/AUFHEBUNG_CCMI")

  def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    import java.util.GregorianCalendar

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val doc = v00.CMRevoke(
      v00.MarketParticipantDirectory(
        RoutingHeader(
          RoutingAddress(message.sender, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          RoutingAddress(message.receiver, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          Helper.toCalendar(calendar)
        ),
        Number01,
        v00.AUFHEBUNG_CCMI,
        Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentMode](Config.interfaceMode match {
            case "SIMU" => SIMU
            case _ => PROD
          })),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[v00.SchemaVersion](v00.Number01u4600)),
        )
      ),
      v00.ProcessDirectory(
        message.messageId.get,
        message.conversationId,
        message.requestId.get,
        message.meter.map(x => x.meteringPoint).get,
        Helper.toCalendar(dateFmt.format(message.consentEnd.getOrElse(new Date).getTime)),
        message.reason,
      )
    )

    scalaxb.toXML[v00.CMRevoke](doc, Some("http://www.ebutilities.at/schemata/customerconsent/cmrevoke/01p00"), rootNodeLabel,
      scalaxb.toScope(
        Some("rv") -> "http://www.ebutilities.at/schemata/customerconsent/cmrevoke/01p00",
        Some("ct") -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
        Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance",
      ),
      true).head
  }
}

object CMRevokeXMLMessageV0100 extends EdaResponseType {
  override def fromXML(xmlFile: Elem): Try[CMRevokeMessage] = {
    Try(scalaxb.fromXML[v00.CMRevoke](xmlFile)).map(document => {
      CMRevokeMessage(
        EbMsMessage(
          messageId = Some(document.ProcessDirectory.MessageId),
          conversationId = document.ProcessDirectory.ConversationId,
          sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
          receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
          messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
          messageCodeVersion = Some("01.01"),
          responseData = Some(List(ResponseData(
            Some(document.ProcessDirectory.MeteringPoint),
            List(1099),
            Some(document.ProcessDirectory.ConsentEnd.toGregorianCalendar().getTime.getTime))))
        )
      )
    }
    )
  }
}
