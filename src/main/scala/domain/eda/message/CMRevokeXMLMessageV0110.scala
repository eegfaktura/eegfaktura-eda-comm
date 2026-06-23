package at.energydash
package domain.eda.message

import config.Config
import model.enums.EbMsMessageType
import model.{EbMsMessage, ResponseData}

import scalaxb.Helper
// VALUE/CUS-Variante des CMRevoke auf Schema 01p10 (CM_REV_IMP / CM_REV_CUS).
// Im Gegensatz zur SP-Variante liegt für VALUE/CUS keine eindeutige Zuordnung
// Prozess-Version -> Schema-Version vor, daher erkennt fromXML das Schema selbst:
// zuerst 01p10, dann Fallback auf 01p00, dann auf die CMNotification-Quittung.
// So wird unabhängig vom Stand des Netzbetreibers nie eine Abmeldung verworfen.
import xmlprotocol.{AddressType, DocumentMode, ECNumber, Number01, PROD, RoutingAddress, RoutingHeader, SIMU}
import xmlprotocol.cmrevoke.{v01p00 => v00, v01p10 => v10}

import java.util.{Calendar, Date, GregorianCalendar}
import scala.util.Try
import scala.xml.{Elem, Node}

case class CMRevokeXMLMessageV0110(message: EbMsMessage) extends EdaXMLMessage[v10.CMRevoke] {
  override def rootNodeLabel: Some[String] = Some("CMRevoke")

  override def schemaLocation: Option[String] =
    Some("http://www.ebutilities.at/schemata/customerconsent/cmrevoke/01p10 " +
      "http://www.ebutilities.at/schemata/customerprocesses/CM_REV_IMP/01.00/AUFHEBUNG_CCMI")

  def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val doc = v10.CMRevoke(
      v10.MarketParticipantDirectory(
        RoutingHeader(
          RoutingAddress(message.sender, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          RoutingAddress(message.receiver, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          Helper.toCalendar(calendar)
        ),
        Number01,
        v00.AUFHEBUNG_CCMI.toString,
        Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentMode](Config.interfaceMode match {
            case "SIMU" => SIMU
            case _ => PROD
          })),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[v10.SchemaVersion](v10.Number01u4610)),
        )
      ),
      v10.ProcessDirectory(
        MessageId = message.messageId.get,
        ConversationId = message.conversationId,
        ConsentId = message.requestId.get,
        MeteringPoint = message.meter.map(x => x.meteringPoint).get,
        ConsentEnd = Helper.toCalendar(dateFmt.format(message.consentEnd.getOrElse(new Date).getTime)),
        ReasonKey = None,
        Reason = message.reason,
      )
    )

    scalaxb.toXML[v10.CMRevoke](doc, Some("http://www.ebutilities.at/schemata/customerconsent/cmrevoke/01p10"), rootNodeLabel,
      scalaxb.toScope(
        Some("rv") -> "http://www.ebutilities.at/schemata/customerconsent/cmrevoke/01p10",
        Some("ct") -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
        Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance",
      ),
      true).head
  }
}

object CMRevokeXMLMessageV0110 extends EdaResponseType {
  override def fromXML(xmlFile: Elem): Try[CMRevokeMessage] = {
    Try(scalaxb.fromXML[v10.CMRevoke](xmlFile))
      .map(document =>
        CMRevokeMessage(
          EbMsMessage(
            messageId = Some(document.ProcessDirectory.MessageId),
            conversationId = document.ProcessDirectory.ConversationId,
            sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
            receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
            messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode),
            messageCodeVersion = Some("01.10"),
            responseData = Some(List(ResponseData(
              Some(document.ProcessDirectory.MeteringPoint),
              List(1099),
              Some(document.ProcessDirectory.ConsentEnd.toGregorianCalendar().getTime.getTime))))
          )
        )
      )
      .recoverWith { case _ => CMRevokeXMLMessageV0100.fromXML(xmlFile) }
  }
}
