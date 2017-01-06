/**
 *  Copyright (c) 2016 Intel Corporation 
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.trustedanalytics.scoring

import org.joda.time.DateTime
import spray.json.DefaultJsonProtocol._
import spray.json._
import scala.collection.immutable.Map
import scala.collection.mutable.ArrayBuffer
import org.trustedanalytics.scoring.interfaces.{ Model, Field, ModelMetaData }
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object ScoringServiceJsonProtocol {

  implicit object ModelMetaDataFormat extends JsonFormat[ModelMetaData] {
    override def write(obj: ModelMetaData): JsValue = {
      JsObject(
        "model_type" -> JsString(obj.modelType),
        "model_class" -> JsString(obj.modelClass),
        "model_reader" -> JsString(obj.modelReader),
        "custom_values" -> obj.customMetaData.toJson)
    }

    override def read(json: JsValue): ModelMetaData = ???
  }

  implicit object FieldFormat extends JsonFormat[Field] {
    override def write(obj: Field): JsValue = {
      JsObject(
        "name" -> JsString(obj.name),
        "value" -> JsString(obj.dataType))
    }

    override def read(json: JsValue): Field = {
      val fields = json.asJsObject.fields
      val name = fields.get("name").get.asInstanceOf[JsString].value.toString
      val value = fields.get("data_type").get.asInstanceOf[JsString].value.toString

      Field(name, value)
    }
  }
}

