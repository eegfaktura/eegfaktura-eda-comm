package at.energydash
package domain.eda.messages

import domain.eda.message.{CMRevokeRequestV0100, CMRevokeRequestV0110, CMRevokeXMLMessageV0100, CMRevokeXMLMessageV0110}
import model.EbMsMessage
//import model.enums.EbMsProcessType._
import model.enums.EbMsMessageType

import io.circe.generic.auto._
import io.circe.parser.decode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Success
import scala.xml.Elem

class CMRevokeMessageSpec extends AnyWordSpec with Matchers {

  import model.JsonImplicit._

  "Revoke Message" should {
    "Parse to XML" in {
      val jsonObjectStr =
        """{
          |  "messageId" : "RC100181202306071686177700000000007",
          |  "conversationId" : "TE100001202306071686177700000000008",
          |  "sender" : "TE000001",
          |  "receiver" : "AT009999",
          |  "messageCode" : "AUFHEBUNG_CCMI",
          |  "messageCodeVersion": "01.01",
          |  "requestId" : "CHKWFJ5N",
          |  "meter" : {
          |    "meteringPoint" : "AT0030000000000000000000000000101",
          |    "direction" : null
          |  },
          |  "consentEnd": 1680219900000
          |}
          |""".stripMargin

      val message = decode[EbMsMessage](jsonObjectStr)

      val node = message match {
        case Right(m) => CMRevokeXMLMessageV0100(m).toXML
      }

//      (node \ "ProcessDirectory" \ "ConsentEnd" ).text should fullyMatch regex """[12][0-9]{3}-[01][0-9]-[0-3][0-9]T[012][0-9]:[0-5][0-9]:00[\+]0[12]:00"""
      (node \ "ProcessDirectory" \ "ConsentEnd" ).text shouldBe  "2023-03-31"
      println(node)

      val obj = CMRevokeXMLMessageV0100.fromXML(node.asInstanceOf[Elem])
      obj match {
        case Success(o) =>
          o.message.messageCodeVersion shouldBe Some("01.01")
          o.message.messageCode shouldBe EbMsMessageType.EDA_MSG_AUFHEBUNG_CCMI
          o.message.sender shouldBe "TE000001"
          o.message.responseData.get.head.ConsentEnd shouldBe Some(1680213600000L)
          o.message.responseData.get.head.MeteringPoint shouldBe Some("AT0030000000000000000000000000101")
      }
    }
  }

  "Revoke Message CCMS" should {
    "Parse to XML" in {
      val jsonObjectStr =
        """{
          |  "messageId" : "RC100181202306071686177700000000007",
          |  "conversationId" : "TE100001202306071686177700000000008",
          |  "sender" : "TE000001",
          |  "receiver" : "AT009999",
          |  "messageCode" : "AUFHEBUNG_CCMS",
          |  "messageCodeVersion": "01.01",
          |  "requestId" : "CHKWFJ5N",
          |  "meter" : {
          |    "meteringPoint" : "AT0030000000000000000000000000101",
          |    "direction" : null
          |  },
          |  "consentEnd": 1680219900000
          |}
          |""".stripMargin

      val message = decode[EbMsMessage](jsonObjectStr)

      val node = message match {
        case Right(m) => CMRevokeRequestV0100(m).toXML
      }

      //      (node \ "ProcessDirectory" \ "ConsentEnd" ).text should fullyMatch regex """[12][0-9]{3}-[01][0-9]-[0-3][0-9]T[012][0-9]:[0-5][0-9]:00[\+]0[12]:00"""
      (node \ "ProcessDirectory" \ "ConsentEnd" ).text shouldBe  "2023-03-31"
      println(node)

//      val obj = CMRevokeRequestV0100.fromXML(node.asInstanceOf[Elem])
//      obj match {
//        case Success(o) =>
//          o.message.messageCodeVersion shouldBe Some("01.01")
//          o.message.messageCode shouldBe EbMsMessageType.EDA_MSG_ANTWORT_CCMS
//          o.message.sender shouldBe "TE000001"
//          o.message.responseData.get.head.ConsentEnd shouldBe Some(1680213600000L)
//          o.message.responseData.get.head.MeteringPoint shouldBe Some("AT0030000000000000000000000000101")
//      }
    }
  }

  "Revoke Message CCMS Schema 01.10 (SP)" should {
    "build and round-trip including ReasonKey/Reason changes" in {
      val jsonObjectStr =
        """{
          |  "messageId" : "RC100181202306071686177700000000007",
          |  "conversationId" : "TE100001202306071686177700000000008",
          |  "sender" : "TE000001",
          |  "receiver" : "AT009999",
          |  "messageCode" : "AUFHEBUNG_CCMS",
          |  "messageCodeVersion": "01.20",
          |  "requestId" : "CHKWFJ5N",
          |  "meter" : {
          |    "meteringPoint" : "AT0030000000000000000000000000101",
          |    "direction" : null
          |  },
          |  "consentEnd": 1680219900000,
          |  "reason": "Vertragsende"
          |}
          |""".stripMargin

      val message = decode[EbMsMessage](jsonObjectStr)

      val node = message match {
        case Right(m) => CMRevokeRequestV0110(m).toXML
      }

      (node \ "ProcessDirectory" \ "ConsentEnd").text shouldBe "2023-03-31"
      (node \ "MarketParticipantDirectory" \ "@SchemaVersion").text shouldBe "01.10"
      (node \ "MarketParticipantDirectory" \ "MessageCode").text shouldBe "AUFHEBUNG_CCMS"
      // ReasonKey ist optional und wird von uns nie gesetzt (nur 01p10 kennt es)
      (node \ "ProcessDirectory" \ "ReasonKey") shouldBe empty
      (node \ "ProcessDirectory" \ "Reason").text shouldBe "Vertragsende"

      val obj = CMRevokeRequestV0110.fromXML(node.asInstanceOf[Elem])
      obj match {
        case Success(o) =>
          o.message.messageCode shouldBe EbMsMessageType.EDA_MSG_AUFHEBUNG_CCMS
          o.message.messageCodeVersion shouldBe Some("01.20")
          o.message.sender shouldBe "TE000001"
          o.message.requestId shouldBe Some("CHKWFJ5N")
          o.message.reason shouldBe Some("Vertragsende")
      }
    }
  }

  "Revoke Message Schema 01.10 (VALUE/CUS)" should {
    "auto-detect 01.10 and fall back to 01.00 — keine stille Verwerfung" in {
      val jsonObjectStr =
        """{
          |  "messageId" : "RC100181202306071686177700000000007",
          |  "conversationId" : "TE100001202306071686177700000000008",
          |  "sender" : "TE000001",
          |  "receiver" : "AT009999",
          |  "messageCode" : "AUFHEBUNG_CCMI",
          |  "messageCodeVersion": "01.01",
          |  "requestId" : "CHKWFJ5N",
          |  "meter" : {
          |    "meteringPoint" : "AT0030000000000000000000000000101",
          |    "direction" : null
          |  },
          |  "consentEnd": 1680219900000
          |}
          |""".stripMargin

      val message = decode[EbMsMessage](jsonObjectStr).toOption.get

      // 01p10-Pfad: erkannt und geparst
      val node10 = CMRevokeXMLMessageV0110(message).toXML
      (node10 \ "MarketParticipantDirectory" \ "@SchemaVersion").text shouldBe "01.10"
      CMRevokeXMLMessageV0110.fromXML(node10.asInstanceOf[Elem]) match {
        case Success(o) =>
          o.message.messageCode shouldBe EbMsMessageType.EDA_MSG_AUFHEBUNG_CCMI
          o.message.messageCodeVersion shouldBe Some("01.10")
          o.message.responseData.get.head.MeteringPoint shouldBe Some("AT0030000000000000000000000000101")
      }

      // 01p00-Fallback: dieselbe Handler-Instanz parst auch das alte Schema
      val node00 = CMRevokeXMLMessageV0100(message).toXML
      CMRevokeXMLMessageV0110.fromXML(node00.asInstanceOf[Elem]) match {
        case Success(o) =>
          o.message.messageCode shouldBe EbMsMessageType.EDA_MSG_AUFHEBUNG_CCMI
          o.message.messageCodeVersion shouldBe Some("01.01")
      }
    }
  }
}
