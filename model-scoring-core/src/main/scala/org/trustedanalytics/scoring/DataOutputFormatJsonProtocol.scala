package org.trustedanalytics.scoring

import org.joda.time.DateTime
import org.trustedanalytics.scoring.ScoringServiceJsonProtocol.DataTypeJsonFormat
import org.trustedanalytics.scoring.interfaces.Model
import spray.json._

import scala.collection.mutable.ArrayBuffer

class DataOutputFormatJsonProtocol(model: Model) {

  implicit object DataInputFormat extends JsonFormat[Seq[Array[Any]]] {

    //don't need this method. just there to satisfy the API.
    override def write(obj: Seq[Array[Any]]): JsValue = ???

    override def read(json: JsValue): Seq[Array[Any]] = {
      val records = json.asJsObject.getFields("records") match {
        case Seq(JsArray(records)) => records
        case x => deserializationError(s"Expected array of records but got $x")
      }
      decodeRecords(records)
    }
  }

  implicit object DataOutputFormat extends JsonFormat[Array[Map[String, Any]]] {

    override def write(obj: Array[Map[String, Any]]): JsValue = {
      JsObject("data" -> new JsArray(obj.map(output => DataTypeJsonFormat.write(output)).toList))
    }

    //don't need this method. just there to satisfy the API.
    override def read(json: JsValue): Array[Map[String, Any]] = ???
  }


  def decodeRecords(records: List[JsValue]): Seq[Array[Any]] = {
    val decodedRecords: Seq[Map[String, Any]] = records.map { record =>
      record match {
        case JsObject(fields) =>
          val decodedRecord: Map[String, Any] = for ((feature, value) <- fields) yield (feature, decodeJValue(value))
          decodedRecord
      }
    }
    var features: Seq[Array[Any]] = Seq[Array[Any]]()
    decodedRecords.foreach(decodedRecord => {
      val featureArray = new Array[Any](decodedRecord.size)
      var counter = 0
      decodedRecord.foreach({
        case (name, value) => {
          featureArray(counter) = value
          counter = counter + 1
        }
      })
      features = features :+ featureArray
    })
    features
  }

  def decodeJValue(v: JsValue): Any = {
    v match {
      case JsString(s) => s
      case JsNumber(n) => n.toDouble
      case JsArray(items) => for (item <- items) yield decodeJValue(item)
      case JsNull => null
      case JsObject(fields) =>
        val decodedValue: Map[String, Any] = for ((feature, value) <- fields) yield (feature, decodeJValue(value))
        decodedValue
      case x => deserializationError(s"Unexpected JSON type in record $x")
    }
  }



}
