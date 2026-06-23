package at.energydash
package domain.eda.message

import config.Config
import model.enums.{EbMsMessageType, EcDisModelEnum, EcTypeEnum, MeterDirectionType}
import model.{EbMsMessage, ResponseData}

import scalaxb.Helper
import xmlprotocol.{AddressType, DocumentMode, ECNumber, Number01, PROD, RoutingAddress, RoutingHeader, SIMU}
import xmlprotocol.ecmplist.{v01p10 => ecmp10}
import xmlprotocol.cpnotification.{v01p13 => cpn13}

import java.util.{Calendar, Date}
import scala.util.Try
import scala.xml.{Elem, Node}


case class ECPartitionChangeMessage(message: EbMsMessage) extends EdaMessage {
  override def getVersion(version: Option[String] = None): EdaXMLMessage[_] = ECPartitionChangeXMLMessage(message)
}

case class ECPartitionChangeXMLMessage(message: EbMsMessage) extends EdaXMLMessage[ecmp10.ECMPList] {

  override def rootNodeLabel: Some[String] = Some("ECMPList")

  override def schemaLocation: Option[String] =
    Some("http://www.ebutilities.at/schemata/customerprocesses/ecmplist/01p10 " +
      "http://www.ebutilities.at/schemata/customerprocesses/EC_PRTFACT_CHANGE/01.00/ANFORDERUNG_CPF")

  override def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    val now = new Date
    val processDate = Calendar.getInstance
    processDate.add(Calendar.DATE, 1)

    val doc = ecmp10.ECMPList(
      MarketParticipantDirectory=ecmp10.MarketParticipantDirectory(
        RoutingHeader=RoutingHeader(
          RoutingAddress(message.sender, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          RoutingAddress(message.receiver, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          Helper.toCalendar(MessageHelper.buildCalendar(now))
        ),
        Sector=Number01,
        MessageCode="ANFORDERUNG_CPF",
        attributes=Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentMode](Config.interfaceMode match {
            case "SIMU" => SIMU
            case _ => PROD
          })),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[ecmp10.SchemaVersion](ecmp10.Number01u4610)),
        )
      ),
      ProcessDirectory=ecmp10.ProcessDirectory(
        MessageId = message.messageId.get,
        ConversationId = message.conversationId,
        ProcessDate = Helper.toCalendar(MessageHelper.buildCalendarDate(processDate.getTime)),
        ECID = message.ecId.get,
        ECType = message.ecType match {
          case Some(EcTypeEnum.GEA) => ecmp10.GC
          case Some(EcTypeEnum.REGIONAL) => ecmp10.RC_R
          case Some(EcTypeEnum.BEG) => ecmp10.CC
          case _ => ecmp10.RC_L
        },
        ECDisModel = message.ecDisModel match {
          case Some(EcDisModelEnum.STATIC) => ecmp10.S
          case _ => ecmp10.D
        },
        MPListData = message.meterList.get.map(m =>
          ecmp10.MPListData(MeteringPoint = m.meteringPoint,
            MPTimeData = Seq(ecmp10.MPTimeData(
              DateFrom = Helper.toCalendar(MessageHelper.buildCalendarDate(now)),
              DateTo = Helper.toCalendar("2099-12-31"),
              EnergyDirection = m.direction match {
                case Some(MeterDirectionType.CONSUMPTION) => ecmp10.CONSUMPTION
                case _ => ecmp10.GENERATION
              },
              ECPartFact = m.partFact.get,
              DateActivate = Helper.toCalendar(MessageHelper.buildCalendarDate(m.activation.get)),
            ))))
      )
    )

    scalaxb.toXML[ecmp10.ECMPList](doc, Some("http://www.ebutilities.at/schemata/customerprocesses/ecmplist/01p10"), rootNodeLabel,
      scalaxb.toScope(
        Some("rv") -> "http://www.ebutilities.at/schemata/customerprocesses/ecmplist/01p10",
        Some("ct") -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
        Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance",
      ),
      true).head
  }
}

object ECPartitionChangeXMLMessage extends EdaResponseType {
  override def fromXML(xmlFile: Elem): Try[ECPartitionChangeMessage] =
    Try(scalaxb.fromXML[cpn13.CPNotification](xmlFile)).map(document =>
      ECPartitionChangeMessage(
        EbMsMessage(
          messageId = Some(document.ProcessDirectory.MessageId),
          conversationId = document.ProcessDirectory.ConversationId,
          sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
          receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
          messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
          messageCodeVersion = Some("01.20"),
          responseData = Some(document.ProcessDirectory.ResponseData.ResponseCode.map(r => ResponseData(None, List(r)))),
        )
      )
    )
}
