package at.energydash
package domain.eda.message

import model._
import model.enums.EbMsMessageType
import config.Config

import akka.util.ByteString
import scalaxb.Helper
import xmlprotocol.{DocumentMode, Number01, PROD, RoutingAddress, RoutingHeader, SIMU}
import xmlprotocol.consumptionrecord.{v01p30 => cr30, v01p31 => cr31, v01p40 => cr40}

import java.io.StringWriter
import java.util.{Calendar, Date}
import scala.util.Try
import scala.xml.{Elem, Node, XML}

case class ConsumptionRecordMessage(message: EbMsMessage) extends EdaMessage {
  override def getVersion(version: Option[String]) = version match {
    case Some("03.03") => ConsumptionRecordMessageV0130(message)
    case _ => ConsumptionRecordMessageV0130(message)
  }
}
case class ConsumptionRecordMessageV0130(message: EbMsMessage) extends EdaXMLMessage[cr30.ConsumptionRecord] {
  override def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    import java.util.GregorianCalendar

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processCalendar = new GregorianCalendar(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    processCalendar.add(Calendar.DAY_OF_MONTH, 3)

    val doc = cr30.ConsumptionRecord(
      cr30.MarketParticipantDirectory(
        RoutingHeader(
          RoutingAddress(message.sender),
          RoutingAddress(message.receiver),
          Helper.toCalendar(calendar)
        ),
        Number01,
        cr30.DATEN_CRMSG,
        Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentMode](Config.interfaceMode match {
            case "SIMU" => SIMU
            case _ => PROD
          })),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[cr30.ConsumptionRecordVersion](cr30.Number01u4630)),
        )

      ),
      cr30.ProcessDirectory(
        message.messageId.get,
        message.conversationId,
        Helper.toCalendar(calendar),
        message.meter.map(x => x.meteringPoint).get
      )
    )

    scalaxb.toXML[cr30.ConsumptionRecord](doc, Some("http://www.ebutilities.at/schemata/customerprocesses/consumptionrecord/01p30"), Some("ConsumptionRecord"),
      scalaxb.toScope(None -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20"), true).head
  }

  override def toByte: Try[ByteString] = Try {

    val xml = toXML

    val xmlString = new StringWriter()
    XML.write(xmlString, xml, "UTF-8", true, null)

    ByteString.fromString(xmlString.toString)
  }
}

object ConsumptionRecordMessageV0130 extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[ConsumptionRecordMessage] = {
    Try(scalaxb.fromXML[cr30.ConsumptionRecord](xmlFile)).map(document =>
      ConsumptionRecordMessage(
        EbMsMessage(
          messageId = Some(document.ProcessDirectory.MessageId),
          conversationId = document.ProcessDirectory.ConversationId,
          sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
          receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
          messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
          messageCodeVersion = Some("03.00"),
          meter = Some(Meter(document.ProcessDirectory.MeteringPoint, None)),
          energy = Some(document.ProcessDirectory.Energy.map(energy => Energy(
            energy.MeteringPeriodStart.toGregorianCalendar.getTime,
            energy.MeteringPeriodEnd.toGregorianCalendar.getTime,
            energy.MeteringIntervall.toString,
            energy.NumberOfMeteringIntervall,
            data=energy.EnergyData.map(v =>
                EnergyData(
                  v.MeterCode,
                  v.EP.map(vv => EnergyValue(
                    vv.DTF.toGregorianCalendar.getTime,
                    vv.DTT.map(dtt=>dtt.toGregorianCalendar.getTime),
                    vv.MM.map(mm => mm.toString),
                    vv.BQ
                ))
              )
            )
          )).head
          ),
        )
      )
    )
  }
}

object ConsumptionRecordMessageV0303 extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[ConsumptionRecordMessage] = {
    Try(scalaxb.fromXML[cr31.ConsumptionRecord](xmlFile)).map(document =>
      ConsumptionRecordMessage(
        EbMsMessage(
          messageId = Some(document.ProcessDirectory.MessageId),
          conversationId = document.ProcessDirectory.ConversationId,
          sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
          receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
          messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
          messageCodeVersion = Some("03.03"),
          meter = Some(Meter(document.ProcessDirectory.MeteringPoint, None)),
          energy = Some(document.ProcessDirectory.Energy.map(energy => Energy(
            energy.MeteringPeriodStart.toGregorianCalendar.getTime,
            energy.MeteringPeriodEnd.toGregorianCalendar.getTime,
            energy.MeteringIntervall.toString,
            energy.NumberOfMeteringIntervall,
            data=energy.EnergyData.map(v =>
              EnergyData(
                v.MeterCode,
                v.EP.map(vv => EnergyValue(
                  vv.DTF.toGregorianCalendar.getTime,
                  Some(vv.DTT.toGregorianCalendar.getTime),
                  vv.MM.map(mm => mm.toString),
                  vv.BQ
                ))
              )
            )
          )).head),
        )
      )
    )
  }
}

object ConsumptionRecordMessageV0410 extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[ConsumptionRecordMessage] = {
    Try(scalaxb.fromXML[cr40.ConsumptionRecord](xmlFile)).map(document =>
      ConsumptionRecordMessage(
        EbMsMessage(
          messageId = Some(document.ProcessDirectory.MessageId),
          conversationId = document.ProcessDirectory.ConversationId,
          sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
          receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
          messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
          messageCodeVersion = Some("03.10"),
          ecId = document.ProcessDirectory.ECID,
          meter = Some(Meter(document.ProcessDirectory.MeteringPoint, None)),
          energy = Some(document.ProcessDirectory.Energy.map(energy => Energy(
            energy.MeteringPeriodStart.toGregorianCalendar.getTime,
            energy.MeteringPeriodEnd.toGregorianCalendar.getTime,
            energy.MeteringIntervall.toString,
            energy.NumberOfMeteringIntervall,
            data=energy.EnergyData.map(v =>
              EnergyData(
                v.MeterCode,
                v.EP.map(vv => EnergyValue(
                  vv.DTF.toGregorianCalendar.getTime,
                  Some(vv.DTT.toGregorianCalendar.getTime),
                  Some(vv.MM.toString),
                  vv.BQ
                ))
              )
            )
          )).head),
        )
      )
    )
  }
}
