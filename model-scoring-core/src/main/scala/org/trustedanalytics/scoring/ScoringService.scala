/**
  * Copyright (c) 2016 Intel Corporation 
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *       http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package org.trustedanalytics.scoring

import java.io.File

import akka.actor.{Actor, Props}
import org.apache.commons.io.FileUtils
import org.trustedanalytics.model.archive.format.ModelArchiveFormat
import spray.routing._
import spray.http._
import spray.http.BodyPart
import MediaTypes._
import akka.event.Logging
import com.typesafe.config.ConfigFactory

import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import org.trustedanalytics.scoring.interfaces.Model
import spray.json._
import DefaultJsonProtocol._
import org.trustedanalytics.scoring.ScoringServiceJsonProtocol._
import spray.routing.directives.RouteDirectives


/**
  * We don't implement our route structure directly in the service actor because
  * we want to be able to test it independently, without having to spin up an actor
  *
  * @param scoringService the service to delegate to
  */
class ScoringServiceActor(val scoringService: ScoringService) extends Actor with HttpService {

  /**
    * the HttpService trait defines only one abstract member, which
    * connects the services environment to the enclosing actor or test
    */
  override def actorRefFactory = context

  /**
    * Delegates to Scoring Service.
    *
    * This actor only runs our route, but you could add other things here, like
    * request stream processing or timeout handling
    */
  def receive = runRoute(scoringService.serviceRoute)
}

/**
  * Defines our service behavior independently from the service actor
  */
class ScoringService(model: Model) extends Directives {

  import spray.json._

  var scoringModel = model

  lazy val description = {
    new ServiceDescription(name = "Trusted Analytics",
      identifier = "ia",
      versions = List("v1", "v2"))
  }

  /**
    * Case class to hold model, model tar file path and json format protocol object
    *
    * @param model      Scoring Model
    * @param jsonFormat DataOutputFormatJsonProtocol object
    */
  case class ModelData(model: Model, jsonFormat: DataOutputFormatJsonProtocol)

  var modelData = ModelData(scoringModel, new DataOutputFormatJsonProtocol(scoringModel))

  /**
    * Main Route entry point to the Scoring Server
    */
  val serviceRoute: Route = logRequest("scoring service", Logging.InfoLevel) {
    val prefix = "score"
    val metadataPrefix = "metadata"
    path("") {
      get {
        respondWithMediaType(`text/html`) {
          complete(
            getHomePage(modelData)
          )
        }
      }
    } ~
      path("v2" / prefix) {
        requestUri { uri =>
          post {
            entity(as[String]) {
              scoreArgs =>
                val json: JsValue = scoreArgs.parseJson
                getScore(modelData, json)
            }
          }
        }
      } ~
      path("v1" / prefix) {
        requestUri { uri =>
          parameterSeq { (params) =>
            val sr = params.toArray
            var records = Seq[Array[Any]]()
            for (i <- sr.indices) {
              val decoded = java.net.URLDecoder.decode(sr(i)._2, "UTF-8")
              val splitSegment = decoded.split(",")
              records = records :+ splitSegment.asInstanceOf[Array[Any]]
            }
            getScoreV1(modelData, records)
          }
        }
      } ~
      path("v2" / metadataPrefix) {
        requestUri { uri =>
          get {
            getMetaData(modelData)
          }
        }
      } ~
      path("uploadMarFile") {
        post {
          var ret: Option[Route] = None
          entity(as[MultipartFormData]) { formData =>
            val file = formData.get("file")
            for (fileBodypart: BodyPart <- file) {
              val fileEntity: HttpEntity = fileBodypart.entity
              val status = installModel(modelData, fileEntity.data.toByteArray)
              if (status == "OK") {
                ret = Some(complete("File was successfully uploaded and model created. \n"))
              }
              else {
                ret = Some(complete("Model already installed in SE. App does not allow uploading another model. \n"))
              }
            }
            ret.getOrElse(complete(StatusCodes.InternalServerError, "Unable to create the model"))
          }
        }
      } ~
      path("uploadMarBytes") {
        requestUri { uri =>
          post {
            var ret: Option[Route] = None
            entity(as[Array[Byte]]) { params =>
              val status = installModel(modelData, params)
              if (status == "OK") {
                ret = Some(complete("Model Bytes were successfully uploaded and model created. \n"))
              }
              else {
                ret = Some(complete("Model already installed in SE. App does not allow uploading another model. \n"))
              }
              ret.getOrElse(complete(StatusCodes.InternalServerError, "Unable to create the model"))
            }
          }
        }
      } ~
      path("v2" / "reviseMarFile") {
        requestUri { uri =>
          post {
            this.synchronized {
              entity(as[MultipartFormData]) { formData =>
                processReviseMarFile(formData, false)
              }
            }
          }
        }
      } ~
      path("v2" / "forceReviseMarFile") {
        requestUri { uri =>
          post {
            this.synchronized {
              entity(as[MultipartFormData]) { formData =>
                processReviseMarFile(formData, true)
              }
            }
          }
        }
      }~
      path("v2"/ "reviseMarBytes") {
        requestUri { uri =>
          post {
            this.synchronized {
              entity(as[Array[Byte]]) { params =>
                processReviseMarBytes(params, false)
              }
            }
          }
        }
      }~
      path("v2"/ "forceReviseMarBytes") {
        requestUri { uri =>
          post {
            this.synchronized {
              entity(as[Array[Byte]]) { params =>
                processReviseMarBytes(params, true)
              }
            }
          }
        }
      }
  }

  def processReviseMarFile(formData: MultipartFormData, force: Boolean): Route = {
    val file = formData.get("file")
    var ret: Option[Route] = None
    for (fileBodypart: BodyPart <- file) {
      val fileEntity: HttpEntity = fileBodypart.entity
      if (modelData.model == null) {
        ret = Some(complete(StatusCodes.BadRequest, "No existing model present. " +
          "Please use /uploadMarFile or /uploadMarBytes api to upload the model"))
      } else {
        val status = reviseModelData(modelData, fileEntity.data.toByteArray, force)
        if (status == "OK") {
          ret = Some(complete("Model was successfully revised. \n"))
        } else if (status == "Incompatible") {
          ret = Some(complete(StatusCodes.BadRequest, "Revised Model type or input-output parameters names are" +
            " different than existing model"))
        }
      }
    }
    ret.getOrElse(complete(StatusCodes.InternalServerError, "Unable to revise the model") )
  }

  def processReviseMarBytes(params: Array[Byte], force: Boolean): Route = {
    var ret: Option[Route] = None
    if (modelData.model == null) {
      ret = Some(complete(StatusCodes.BadRequest, "No existing model present. " +
        "Please use /uploadMarFile or /uploadMarBytes api to upload the model"))
    } else {
      val status = reviseModelData(modelData, params, force)
      if (status == "OK") {
        ret = Some(complete("Model was successfully revised. \n"))
      } else if (status == "Incompatible") {
        ret = Some(complete(StatusCodes.BadRequest, "Revised Model type or input-output parameters names are" +
          " different than existing model"))
      }
    }
    ret.getOrElse(complete(StatusCodes.InternalServerError, "Unable to revise the model"))
  }
  def scoreStringRequest(modelData: ModelData, records: Seq[Array[Any]]): Array[Any] = {
    records.map(row => {
      val score = modelData.model.score(row)
      score(score.length - 1).toString
    }).toArray
  }

  def scoreJsonRequest(modeldata: ModelData, json: JsValue): Array[Map[String, Any]] = {
    val records = modeldata.jsonFormat.DataInputFormat.read(json)
    records.map(row => scoreToMap(modeldata.model, modeldata.model.score(row))).toArray
  }

  def scoreToMap(model: Model, score: Array[Any]): Map[String, Any] = {
    val outputNames = model.output().map(o => o.name)
    require(score.length == outputNames.length, "Length of output values should match the output names")
    val outputMap: Map[String, Any] = outputNames.zip(score).map(combined => (combined._1.name, combined._2)).toMap
    outputMap
  }

  private def getScore(md: ModelData, json: JsValue): Route = {
    onComplete(Future {
      scoreJsonRequest(md, json)
    }) {
      case Success(output) => complete(md.jsonFormat.DataOutputFormat.write(output).toString())
      case Failure(ex) => ctx => {
        ctx.complete(StatusCodes.InternalServerError, ex.getMessage)
      }
    }
  }

  private def getScoreV1(md: ModelData, records: Seq[Array[Any]]): Route = {
    onComplete(Future {
      scoreStringRequest(md, records)
    }) {
      case Success(string) => complete(string.mkString(","))
      case Failure(ex) => ctx => {
        ctx.complete(StatusCodes.InternalServerError, ex.getMessage)
      }
    }
  }

  private def getMetaData(md: ModelData): Route = {
    import spray.json._
    onComplete(Future {
      md.model.modelMetadata()
    }) {
      case Success(metadata) => complete(JsObject("model_details" -> metadata.toJson,
        "input" -> new JsArray(md.model.input.map(input => FieldFormat.write(input)).toList),
        "output" -> new JsArray(md.model.output.map(output => FieldFormat.write(output)).toList)).toString())
      case Failure(ex) => ctx => {
        ctx.complete(StatusCodes.InternalServerError, ex.getMessage)
      }
    }
  }

  /**
    * Store the MAR file locally and load the model saved at the given path
    *
    * @return String indicating if the model was successfully uploaded ('OK') OR model upload was not attempted since SE already contained a model ('Fail')
    */
  private def installModel(md: ModelData, modelBytes: Array[Byte]): String = {
    if (md.model == null) {
      var tempMarFile: File = null
      try {
        tempMarFile = File.createTempFile("model", ".mar")
        FileUtils.writeByteArrayToFile(tempMarFile, modelBytes)
        val newModel = ModelArchiveFormat.read(tempMarFile, this.getClass.getClassLoader, None)
        modelData = ModelData(newModel, new DataOutputFormatJsonProtocol(newModel))
        "OK"
      }
      finally {
        sys.addShutdownHook(FileUtils.deleteQuietly(tempMarFile)) // Delete temporary File on exit
      }
    }
    else {
      "Fail"
    }
  }

  private def getHomePage(md: ModelData): String = {

    if (md.model != null) {
      val metadata = JsObject("model_details" -> md.model.modelMetadata().toJson,
        "input" -> new JsArray(md.model.input.map(input => FieldFormat.write(input)).toList),
        "output" -> new JsArray(md.model.output.map(output => FieldFormat.write(output)).toList)).prettyPrint

      s"""
      <html>
        <body>
          <h1>Welcome to the Scoring Engine</h1>
          <h3>Model details:</h3>
          Model metadata:<pre> $metadata </pre>
        </body>
      </html>"""

    } else {
      s"""
      <html>
        <body>
          <h1>Welcome to the Scoring Engine</h1>
          <h3>Model not present, Please upload the model</h3>
        </body>
      </html>"""
    }
  }

  private def reviseModelData(md: ModelData, modelBytes: Array[Byte], force: Boolean = false): String = {
    var tempMarFile: File = null
    try {
      tempMarFile = File.createTempFile("model", ".mar")
      FileUtils.writeByteArrayToFile(tempMarFile, modelBytes)
      val revisedModel = ModelArchiveFormat.read(tempMarFile, this.getClass.getClassLoader, None)
      if (force || ScoringEngineHelper.isModelCompatible(modelData.model, revisedModel)) {
        modelData = ModelData(revisedModel, new DataOutputFormatJsonProtocol(revisedModel))
        "OK"
      }
      else {
        "Incompatible"
      }
    }
    finally {
      sys.addShutdownHook(FileUtils.deleteQuietly(tempMarFile)) // Delete temporary File on exit
    }
  }
}

case class ServiceDescription(name: String, identifier: String, versions: List[String])

