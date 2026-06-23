package at.energydash
package domain.eda.message

import model.ResponseData

import xmlprotocol.cmnotification.{v01p11 => v11}

import scala.util.Try
import scala.xml.Elem

case class ParsedCMNotification(
  messageId: String,
  conversationId: String,
  sender: String,
  receiver: String,
  messageCode: String,
  cmRequestId: String,
  responseData: List[ResponseData],
)

object CMNotificationParser {

  def parse(xmlFile: Elem): Try[ParsedCMNotification] =
    Try(scalaxb.fromXML[v11.CMNotification](xmlFile)).map(fromV11)

  private def fromV11(d: v11.CMNotification): ParsedCMNotification =
    ParsedCMNotification(
      messageId = d.ProcessDirectory.MessageId,
      conversationId = d.ProcessDirectory.ConversationId,
      sender = d.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
      receiver = d.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
      messageCode = d.MarketParticipantDirectory.MessageCode,
      cmRequestId = d.ProcessDirectory.CMRequestId,
      responseData = d.ProcessDirectory.ResponseData
        .map(r => ResponseData(r.MeteringPoint, r.ResponseCode)).toList,
    )
}
