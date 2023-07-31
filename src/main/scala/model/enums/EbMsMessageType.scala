package at.energydash
package model.enums

import io.circe.{Decoder, Encoder}

object EbMsMessageType extends Enumeration {
  type EbMsMessageType = Value

  implicit val decoder: Decoder[Value] = Decoder.decodeEnumeration(this)
  implicit val encoder: Encoder[Value] = Encoder.encodeEnumeration(this)

  val ENERGY_FILE_RESPONSE: EbMsMessageType.Value = Value("DATEN_CRMSG")

  val ONLINE_REG_INIT: EbMsMessageType.Value = Value("ANFORDERUNG_ECON")
  // EC_REG_ONL Process
  val ONLINE_REG_ANSWER: EbMsMessageType.Value = Value("ANTWORT_ECON")
  val ONLINE_REG_REJECTION: EbMsMessageType.Value = Value("ABLEHNUNG_ECON")
  val ONLINE_REG_APPROVAL: EbMsMessageType.Value = Value("ZUSTIMMUNG_ECON")
  val ONLINE_REG_COMPLETION: EbMsMessageType.Value = Value("ABSCHLUSS_ECON")

  // Energy Sync and Energy Message
  val ENERGY_SYNC_REQ: EbMsMessageType.Value = Value("ANFORDERUNG_PT")
  val ENERGY_SYNC_RES: EbMsMessageType.Value = Value("ANTWORT_PT")
  val ENERGY_SYNC_REJECTION: EbMsMessageType.Value = Value("ABLEHNUNG_PT")

  val EDA_MSG_AUFHEBUNG_CCMI: EbMsMessageType.Value = Value("AUFHEBUNG_CCMI")
  val EDA_MSG_AUFHEBUNG_CCMC: EbMsMessageType.Value = Value("AUFHEBUNG_CCMC")
  val EDA_MSG_AUFHEBUNG_CCMS: EbMsMessageType.Value = Value("AUFHEBUNG_CCMS")

  val ZP_LIST: EbMsMessageType.Value = Value("ANFORDERUNG_ECP")
  val ZP_LIST_RESPONSE: EbMsMessageType.Value = Value("SENDEN_ECP")
  val EEG_BASE_DATA: EbMsMessageType.Value = Value("ANFORDERUNG_GN")
  val ERROR_MESSAGE: EbMsMessageType.Value = Value("ERROR_MESSAGE")


}
