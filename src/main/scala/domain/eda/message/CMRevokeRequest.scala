package at.energydash
package domain.eda.message

import config.Config
import model.enums.EbMsMessageType
import model.EbMsMessage

import scalaxb.Helper
import xmlprotocol.{AddressType, DocumentMode, ECNumber, Number01, PROD, RoutingAddress, RoutingHeader, SIMU}
import xmlprotocol.cmrevoke.{v01p00 => v00}

import java.util.{Calendar, Date}
import scala.util.Try
import scala.xml.{Elem, Node}
case class CMRevokeRequest(message: EbMsMessage) extends EdaMessage {
  override def getVersion(version: Option[String] = None): EdaXMLMessage[_] = message.messageCodeVersion match {
    case Some("01.30") | Some("01.20") => CMRevokeRequestV0110(message)
    case _ => CMRevokeRequestV0100(message)
  }
}

case class CMRevokeRequestV0100(message: EbMsMessage) extends EdaXMLMessage[v00.CMRevoke] {
  override def rootNodeLabel: Some[String] = Some("CMRevoke")

  override def schemaLocation: Option[String] =
  Some("http://www.ebutilities.at/schemata/customerconsent/cmrevoke/01p00 " +
    "http://www.ebutilities.at/schemata/customerprocesses/CM_REV_SP/01.02/AUFHEBUNG_CCMS")

  def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    import java.util.GregorianCalendar

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processDate = Calendar.getInstance
    processDate.add(Calendar.DATE, 1)

    val doc = v00.CMRevoke(
      v00.MarketParticipantDirectory(
        RoutingHeader(
          RoutingAddress(message.sender, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          RoutingAddress(message.receiver, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          Helper.toCalendar(calendar)
        ),
        Number01,
        v00.AUFHEBUNG_CCMS,
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
        MessageId = message.messageId.get,
        ConversationId = message.conversationId,
        ConsentId = message.requestId.get,
        MeteringPoint = message.meter.map(x => x.meteringPoint).get,
        ConsentEnd = Helper.toCalendar(dateFmt.format(message.consentEnd.getOrElse(new Date).getTime)),
        Reason = message.reason,
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

object CMRevokeRequestV0100 extends EdaResponseType {
  override def fromXML(xmlFile: Elem): Try[CMRevokeRequest] = {
    CMNotificationParser.parse(xmlFile).map(p =>
      CMRevokeRequest(
        EbMsMessage(
          messageId = Some(p.messageId),
          conversationId = p.conversationId,
          sender = p.sender,
          receiver = p.receiver,
          messageCode = EbMsMessageType.withName(p.messageCode),
          messageCodeVersion = Some("01.11"),
          responseData = Some(p.responseData)
        )
      )
    )
  }
}
