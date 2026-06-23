package at.energydash
package domain.eda.message

import model.EbMsMessage
import model.enums.EbMsMessageType._
import model.enums.EbMsProcessType._
import utils.zip.CRC8

import com.google.common.io.BaseEncoding
import org.slf4j.LoggerFactory

import java.text.SimpleDateFormat
import java.util.zip.CRC32
import java.util.{Calendar, Date, GregorianCalendar}

object MessageHelper {

  var logger = LoggerFactory.getLogger("MessageHelper")

  /**
   * Extract Message Type for Sending to Marktteilnehmer.
   */
  def getEdaMessageByType(message: EbMsMessage): EdaXMLMessage[_] = {
    message.messageCode match {
      case ONLINE_REG_INIT => CMRequestRegistrationOnline(message).getVersion()
      case OFFLINE_REG_INIT => CMRequestOfflineRegistration(message).getVersion()
      case ZP_LIST => CPRequestZPList(message).getVersion()
      case EEG_BASE_DATA => CPRequestBaseData(message).getVersion()
      case ENERGY_SYNC_REQ => CPRequestMeteringValue(message).getVersion()
      case EDA_MSG_AUFHEBUNG_CCMS => CMRevokeRequest(message).getVersion()
      case CHANGE_METER_PARTITION => ECPartitionChangeMessage(message).getVersion()
    }
  }

  /**
   * Lookup for Message object according to Message type. Incoming messages.
   * @param processCode
   * @param version
   * @return
   */
  def getEdaMessageFromHeader(processCode: EbMsProcessType, version: String): Option[EdaResponseType] = {
    processCode match {
      case PROCESS_ENERGY_RESPONSE => {
        version match {
          case "03.03" => Some(ConsumptionRecordMessageV0303)
          case "03.10" => Some(ConsumptionRecordMessageV0410)
          case _ => Some(ConsumptionRecordMessageV0130)
        }
      }
      case PROCESS_REGISTER_ONLINE =>
        version match {
          case "02.00" => Some(CMRequestRegistrationOnlineXMLMessageV0200)
          case _ => Some(CMRequestRegistrationOnlineXMLMessageV0110)
        }
      case PROCESS_REGISTER_OFFLINE => Some(CMRequestOfflineRegistrationXMLMessage)
      case PROCESS_LIST_METERINGPOINTS => Some(CPRequestZPListXMLMessage)
      case PROCESS_METERINGPOINTS_VALUE => Some(CPRequestMeteringValueXMLMessage)
      case PROCESS_REVOKE_VALUE | PROCESS_REVOKE_CUS => Some(CMRevokeXMLMessageV0110)
      case PROCESS_REVOKE_SP =>
        version match {
          case "01.30" | "01.20" => Some(CMRevokeRequestV0110)
          case _ => Some(CMRevokeRequestV0100)
        }
      case PROCESS_EC_PRTFACT_CHANGE => {
        version match {
          case "01.00" => Some(ECPartitionChangeXMLMessage)
          case _ => Some(EdaWrongVersionXMLMessage)
        }
      }
      case _ =>
        logger.warn(s"Wrong ProcessCode: ${processCode}")
        None
    }
  }

  def EDAMessageCodeToProcessCode(msCode: EbMsMessageType): EbMsProcessType = {
    msCode match {
      case ZP_LIST => PROCESS_LIST_METERINGPOINTS
      case EEG_BASE_DATA => PROCESS_LIST_METERINGPOINTS
      case ONLINE_REG_INIT => PROCESS_REGISTER_ONLINE
      case OFFLINE_REG_INIT => PROCESS_REGISTER_OFFLINE
      case ENERGY_SYNC_REQ => PROCESS_METERINGPOINTS_VALUE
      case EDA_MSG_AUFHEBUNG_CCMC | EDA_MSG_AUFHEBUNG_CCMI => PROCESS_REVOKE_VALUE
      case EDA_MSG_AUFHEBUNG_CCMS => PROCESS_REVOKE_SP
      case CHANGE_METER_PARTITION => PROCESS_EC_PRTFACT_CHANGE
      case _ => throw new RuntimeException("Not able to find Message -> Process mapping")
    }
  }

  def buildRequestId(messageId: String): String = {
    val crc32 = new CRC32()
    crc32.update(messageId.getBytes)
    val crc32Val = crc32.getValue

    val crc8 = new CRC8
    crc8.reset()
    crc8.update(BigInt(crc32Val).toByteArray)

    val compose = BigInt((crc32Val << 8) + crc8.getValue).toByteArray
    BaseEncoding.base32().encode(compose.takeRight(5))
  }

  def buildMessageId(participant: String, seqNumber: Long): String = {
    val cal = Calendar.getInstance
    val dateTime = cal.getTime

    val dateFormat = new SimpleDateFormat("dd")
    val date = dateFormat.format(dateTime)

    val monthFormat = new SimpleDateFormat("MM")
    val month = monthFormat.format(dateTime)

    val yearFormat = new SimpleDateFormat("YYYY")
    val year = yearFormat.format(dateTime)

    s"${participant}${year}${month}${date}${dateTime.getTime / 10000}${formatSeqNumber(seqNumber)}"
  }

  def formatSeqNumber(seqNumber: Long) = f"${seqNumber}%010d"

  def buildCalendar(date: Date): GregorianCalendar = {
    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(date)
    calendar.set(Calendar.MILLISECOND, 0)
    calendar
  }

  def buildCalendarDate(date: Date): String = {
    val format = new SimpleDateFormat("yyyy-MM-dd")
    format.format(date)
  }
}
