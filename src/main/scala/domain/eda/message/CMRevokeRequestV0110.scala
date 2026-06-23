package at.energydash
package domain.eda.message

import config.Config
import model.enums.EbMsMessageType
import model.{EbMsMessage, ResponseData}

import scalaxb.Helper
// CMRevoke-Schema 01p10 (ebUtilities Schema 299, gültig seit 2026-04 zusätzlich
// zu 01p00; Änderungen: Einführung ReasonKey, Entfernung der MessageCode-
// Enumeration). Der SP-Prozess CM_REV_SP fährt mit den Prozess-Versionen 01.20
// und 01.30 auf dieser 01p10-XSD. Ohne diese Variante werden Zählpunkt-
// Abmeldungen umgestellter Netzbetreiber beim Parsen still verworfen.
import xmlprotocol.{AddressType, DocumentMode, ECNumber, Number01, PROD, RoutingAddress, RoutingHeader, SIMU}
import xmlprotocol.cmrevoke.{v01p00 => v00, v01p10 => v10}

import java.util.{Calendar, Date, GregorianCalendar}
import scala.util.Try
import scala.xml.{Elem, Node}

case class CMRevokeRequestV0110(message: EbMsMessage) extends EdaXMLMessage[v10.CMRevoke] {
  override def rootNodeLabel: Some[String] = Some("CMRevoke")

  override def schemaLocation: Option[String] =
    Some("http://www.ebutilities.at/schemata/customerconsent/cmrevoke/01p10 " +
      "http://www.ebutilities.at/schemata/customerprocesses/CM_REV_SP/01.20/AUFHEBUNG_CCMS")

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
        v00.AUFHEBUNG_CCMS.toString,
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

object CMRevokeRequestV0110 extends EdaResponseType {
  override def fromXML(xmlFile: Elem): Try[CMRevokeRequest] = {
    Try(scalaxb.fromXML[v10.CMRevoke](xmlFile))
      .map(document =>
        CMRevokeRequest(
          EbMsMessage(
            messageId = Some(document.ProcessDirectory.MessageId),
            conversationId = document.ProcessDirectory.ConversationId,
            sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
            receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
            messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode),
            messageCodeVersion = Some("01.20"),
            requestId = Some(document.ProcessDirectory.ConsentId),
            consentEnd = Some(document.ProcessDirectory.ConsentEnd.toGregorianCalendar.getTime),
            reason = document.ProcessDirectory.Reason,
          )
        )
      )
      .recoverWith { case _ =>
        CMNotificationParser.parse(xmlFile).map(p =>
          CMRevokeRequest(
            EbMsMessage(
              messageId = Some(p.messageId),
              conversationId = p.conversationId,
              sender = p.sender,
              receiver = p.receiver,
              messageCode = EbMsMessageType.withName(p.messageCode),
              messageCodeVersion = Some("01.20"),
              responseData = Some(p.responseData)
            )
          )
        )
      }
  }
}
