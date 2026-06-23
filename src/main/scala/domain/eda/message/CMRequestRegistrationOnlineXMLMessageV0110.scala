package at.energydash
package domain.eda.message

import config.Config
import model.enums.{EbMsMessageType, MeterDirectionType}
import model.{EbMsMessage, Meter}

import scalaxb.Helper
import xmlprotocol.{AddressType, DocumentMode, ECNumber, Number01, PROD, RoutingAddress, RoutingHeader, SIMU}
import xmlprotocol.cmrequest.{v01p10 => v10, v01p20 => v20}
import xmlprotocol.ecmplist.{v01p00 => ecmp00, v01p10 => ecmp10}

import java.util.{Calendar, Date, GregorianCalendar}
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, Node}

case class CMRequestRegistrationOnline(message: EbMsMessage) extends EdaMessage {
  override def getVersion(version: Option[String] = None): EdaXMLMessage[_] = message.messageCodeVersion match {
    case Some("02.00") => CMRequestRegistrationOnlineXMLMessageV0200(message)
    case _ => CMRequestRegistrationOnlineXMLMessageV0110(message)
  }
}

case class CMRequestRegistrationOnlineXMLMessageV0110(message: EbMsMessage) extends EdaXMLMessage[v10.CMRequest] {
  override def rootNodeLabel: Option[String] = Some("CMRequest")

  override def schemaLocation: Option[String] =
    Some("http://www.ebutilities.at/schemata/customerconsent/cmrequest/01p10 http://www.ebutilities.at/schemata/customerprocesses/EC_REQ_ONL/01.00/ANFORDERUNG_ECON")

  override def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processCalendar = new GregorianCalendar(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    processCalendar.add(Calendar.DAY_OF_MONTH, 3)

    val doc = v10.CMRequest(
      v10.MarketParticipantDirectory(
        RoutingHeader(
          RoutingAddress(message.sender, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          RoutingAddress(message.receiver, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          Helper.toCalendar(calendar)
        ),
        Number01,
        message.messageCode.toString,
        Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentMode](PROD)),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[v10.SchemaVersion](v10.Number01u4610)),
        )

      ),
      v10.ProcessDirectory(
        message.messageId.get,
        message.conversationId,
        Helper.toCalendar(dateFmt.format(calendar.getTime)),
        message.meter.map(x => x.meteringPoint),
        message.requestId.get,
        None,
        v10.ReqType(
          ReqDatType = "EnergyCommunityRegistration",
          DateFrom = Helper.toCalendar(dateFmt.format(processCalendar.getTime)),
          DateTo = Some(Helper.toCalendar(dateFmt.format(new GregorianCalendar(2099, 12, 31).getTime))),
          MeteringIntervall = None,
          TransmissionCycle = None,
          ECID = message.ecId,
          ECShare = None,
          EnergyDirection = message.meter.map { m =>
            m.direction match {
              case Some(MeterDirectionType.CONSUMPTION) => v10.CONSUMPTION
              case Some(MeterDirectionType.GENERATION) => v10.GENERATION
              case _ => v10.CONSUMPTION
            }
          }
        )
      )
    )

    scalaxb.toXML[v10.CMRequest](doc, Some("http://www.ebutilities.at/schemata/customerconsent/cmrequest/01p10"), rootNodeLabel,
      scalaxb.toScope(
        None -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
        Some("ns2") -> "http://www.ebutilities.at/schemata/customerconsent/cmrequest/01p10",
        Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"),
      true).head
  }
}

case class CMRequestRegistrationOnlineXMLMessageV0200(message: EbMsMessage) extends EdaXMLMessage[v20.CMRequest] {
  override def rootNodeLabel: Option[String] = Some("CMRequest")

  override def schemaLocation: Option[String] =
    Some("http://www.ebutilities.at/schemata/customerconsent/cmrequest/01p20 http://www.ebutilities.at/schemata/customerprocesses/EC_REQ_ONL/02.00/ANFORDERUNG_ECON")

  override def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processCalendar = new GregorianCalendar(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    processCalendar.add(Calendar.DAY_OF_MONTH, 3)

    val doc = v20.CMRequest(
      v20.MarketParticipantDirectory(
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
          ("@SchemaVersion", scalaxb.DataRecord[v20.SchemaVersion](v20.Number01u4620)),
        )

      ),
      v20.ProcessDirectory(
        message.messageId.get,
        message.conversationId,
        Helper.toCalendar(dateFmt.format(calendar.getTime)),
        message.meter.map(x => x.meteringPoint),
        message.requestId.get,
        None,
        v20.ReqType(
          ReqDatType = "EnergyCommunityRegistration",
          DateFrom = Helper.toCalendar(dateFmt.format(processCalendar.getTime)),
          DateTo = Some(Helper.toCalendar(dateFmt.format(new GregorianCalendar(2099, 12, 31).getTime))),
          ECID = message.ecId,
          ECPartFact = message.meter.map { m => m.partFact.getOrElse(100) },
          EnergyDirection = message.meter.map { m =>
            m.direction match {
              case Some(MeterDirectionType.CONSUMPTION) => v20.CONSUMPTION
              case Some(MeterDirectionType.GENERATION) => v20.GENERATION
              case None => v20.CONSUMPTION
            }
          }
        )
      )
    )

    scalaxb.toXML[v20.CMRequest](doc, Some("http://www.ebutilities.at/schemata/customerconsent/cmrequest/01p20"), rootNodeLabel,
      scalaxb.toScope(
        None -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
        Some("ns2") -> "http://www.ebutilities.at/schemata/customerconsent/cmrequest/01p20",
        Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"),
      true).head
  }
}


object CMRequestRegistrationOnlineXMLMessageV0110 extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[CMRequestRegistrationOnline] = {
    resolveMessageCode(xmlFile) match {
      case Success(mc) => mc match {
        case EbMsMessageType.ONLINE_REG_COMPLETION => Try(scalaxb.fromXML[ecmp00.ECMPList](xmlFile)).map(document =>
          CMRequestRegistrationOnline(
            EbMsMessage(
              messageId = Some(document.ProcessDirectory.MessageId),
              conversationId = document.ProcessDirectory.ConversationId,
              sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
              receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
              messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
              messageCodeVersion = Some("01.00"),
              meterList = Some(document.ProcessDirectory.MPListData
                .map(mp =>
                  Meter(
                    mp.MeteringPoint,
                    Some(MeterDirectionType.withName(mp.MPTimeData.head.EnergyDirection.toString))
                  )
                )
              ),
            )
          )
        )
        case _ => CMNotificationParser.parse(xmlFile).map(p =>
          CMRequestRegistrationOnline(
            EbMsMessage(
              messageId=Some(p.messageId),
              conversationId=p.conversationId,
              sender=p.sender,
              receiver=p.receiver,
              messageCode=EbMsMessageType.withName(p.messageCode),
              messageCodeVersion=Some("01.11"),
              requestId=Some(p.cmRequestId),
              responseData=Some(p.responseData),
            )
          )
        )
      }
      case Failure(exception) =>
        Try(CMRequestRegistrationOnline(
          EbMsMessage(
            messageCode = EbMsMessageType.ERROR_MESSAGE,
            messageCodeVersion=Some("01.00"),
            conversationId = "1",
            messageId = None,
            sender = "",
            receiver = "",
            errorMessage = Some(exception.getMessage)
          )
        ))
    }
  }
}

object CMRequestRegistrationOnlineXMLMessageV0200 extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[CMRequestRegistrationOnline] = {
    resolveMessageCode(xmlFile) match {
      case Success(mc) => mc match {
        case EbMsMessageType.ONLINE_REG_COMPLETION => Try(scalaxb.fromXML[ecmp10.ECMPList](xmlFile)).map(document =>
          CMRequestRegistrationOnline(
            EbMsMessage(
              messageId = Some(document.ProcessDirectory.MessageId),
              conversationId = document.ProcessDirectory.ConversationId,
              sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
              receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
              messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
              messageCodeVersion = Some("02.00"),
              meterList = Some(document.ProcessDirectory.MPListData
                .flatMap(m =>
                  m.MPTimeData.map(mp =>
                    Meter(
                      meteringPoint=m.MeteringPoint,
                      direction=Some(MeterDirectionType.withName(mp.EnergyDirection.toString)),
                      activation=Some(mp.DateActivate.toGregorianCalendar.getTime),
                      partFact=Some(mp.ECPartFact)
                    ))
                )
              ),
            )
          )
        )
        case _ => CMNotificationParser.parse(xmlFile).map(p =>
          CMRequestRegistrationOnline(
            EbMsMessage(
              messageId=Some(p.messageId),
              conversationId=p.conversationId,
              sender=p.sender,
              receiver=p.receiver,
              messageCode=EbMsMessageType.withName(p.messageCode),
              messageCodeVersion=Some("01.11"),
              requestId=Some(p.cmRequestId),
              responseData=Some(p.responseData),
            )
          )
        )
      }
      case Failure(exception) =>
        Try(CMRequestRegistrationOnline(
          EbMsMessage(
            messageCode = EbMsMessageType.ERROR_MESSAGE,
            messageCodeVersion=Some("01.00"),
            conversationId = "1",
            messageId = None,
            sender = "",
            receiver = "",
            errorMessage = Some(exception.getMessage)
          )
        ))
    }
  }
}
