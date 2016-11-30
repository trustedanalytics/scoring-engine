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

import java.io.File
import java.nio.file.{Path, Files}

import akka.actor.{Props,Actor}
import org.apache.commons.io.FileUtils
import org.trustedanalytics.model.archive.format.ModelArchiveFormat
import spray.json.JsValue
import spray.routing._
import spray.http._
import spray.http.MediaTypes._
import spray.http.BodyPart
import MediaTypes._
import akka.event.Logging
import scala.collection.mutable.ArrayBuffer
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{ Failure, Success }
import org.trustedanalytics.scoring.interfaces.Model
import spray.json._
import java.io.{ByteArrayInputStream, InputStream, OutputStream}

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
  var scoringModel = model

  def homepage = {
    respondWithMediaType(`text/html`) {
      complete {
        <html>
          <body>
            <h1>Welcome to the New Scoring Engine</h1>
          </body>
        </html>
      }
    }
  }

  lazy val description = {
    new ServiceDescription(name = "Trusted Analytics",
      identifier = "ia",
      versions = List("v1", "v2"))
  }

  val jsonFormat = new ScoringServiceJsonProtocol()
  import jsonFormat._

  import spray.json._

  /**
   * Main Route entry point to the Scoring Server
   */
  val serviceRoute: Route = logRequest("scoring service", Logging.InfoLevel) {
    val prefix = "score"
    val metadataPrefix = "metadata"
    path("") {
      get {
        homepage
      }
    } ~
      path("v2" / prefix) {
        requestUri { uri =>
          post {
            entity(as[String]) {
              scoreArgs =>
                val json: JsValue = scoreArgs.parseJson
                import jsonFormat.DataOutputFormat
                onComplete(Future {
                  scoreJsonRequest(DataInputFormat.read(json))
                }) {
                  case Success(output) => complete(DataOutputFormat.write(output).toString())
                  case Failure(ex) => ctx => {
                    ctx.complete(StatusCodes.InternalServerError, ex.getMessage)
                  }
                }
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
            onComplete(Future {
              scoreStringRequest(records)
            }) {
              case Success(string) => complete(string.mkString(","))
              case Failure(ex) => ctx => {
                ctx.complete(StatusCodes.InternalServerError, ex.getMessage)
              }
            }
          }
        }
      } ~
      path("v2" / metadataPrefix) {
        requestUri { uri =>
          get {
            import spray.json._
            require(scoringModel != null, "Model is null. Please use the upload API to add the model to the Scoring Engine")
            onComplete(Future {
              scoringModel.modelMetadata()
            }) {
              case Success(metadata) => complete(JsObject("model_details" -> metadata.toJson,
                "input" -> new JsArray(scoringModel.input.map(input => FieldFormat.write(input)).toList),
                "output" -> new JsArray(scoringModel.output.map(output => FieldFormat.write(output)).toList)).toString)
              case Failure(ex) => ctx => {
                ctx.complete(StatusCodes.InternalServerError, ex.getMessage)

              }
            }
          }
        }
      } ~
      path("upload") {
        post {
          var ret: Option[Route] = None

          entity(as [MultipartFormData]){ formData =>
            val file = formData.get("file")
            for(fileBodypart : BodyPart <- file) {
              val fileEntity: HttpEntity = fileBodypart.entity
              val result = saveAttachment("model.mar", fileEntity.data.toByteArray)
              scoringModel = ModelArchiveFormat.read(new File("model.mar"), this.getClass.getClassLoader, None)
              ret = Some(complete("File was successfully uploaded"))
            }
            ret.getOrElse( complete(StatusCodes.InternalServerError, ""))

          }
        }
      }
  }





    //        requestUri { uri =>
    //          post {
    //            entity(as[String]) { args => val modelBytes = args.parseJson.asJsObject.getFields("mar-bytes")(0)
    //              println(modelBytes)
    //              onComplete(Future { getModel(ModelInputFormat.read(modelBytes))}) {
    //                case Success(m) => complete{scoringModel = m
    //                  HttpResponse(StatusCode.int2StatusCode(200))}
    //                case Failure(ex) => ctx => {
    //                  ctx.complete(StatusCodes.InternalServerError, ex.getMessage)
    //                }
    //              }
    //            }
    //          }
    //        }


  def saveAttachment(fileName: String, content: Array[Byte]): Boolean = {
    println("1 save called")
    saveAttachment[Array[Byte]](fileName, content, {(is, os) => os.write(is)})
    true
  }

  def saveAttachment(fileName: String, content: InputStream): Boolean = {
    println("2 save called")
    saveAttachment[InputStream](fileName, content,
    { (is, os) =>
      val buffer = new Array[Byte](16384)
      Iterator
        .continually (is.read(buffer))
        .takeWhile (-1 !=)
        .foreach (read=>os.write(buffer,0,read))
    }
    )
  }
    def saveAttachment[T](fileName: String, content: T, writeFile: (T, OutputStream) => Unit): Boolean = {
      println("3 save called")
      try {
        val fos = new java.io.FileOutputStream(fileName)
        writeFile(content, fos)
        fos.close()
        true
      } catch {
        case _ => false
      }
    }

  def scoreStringRequest(records: Seq[Array[Any]]): Array[Any] = {
    records.map(row => {
      require(scoringModel != null, "Model is null. Please use the upload API to add the model to the Scoring Engine")
      val score = scoringModel.score(row)
      score(score.length - 1).toString
    }).toArray
  }

  def scoreJsonRequest(records: Seq[Array[Any]]): Array[Map[String, Any]] = {
    records.map(row => {
      require(scoringModel != null, "Model is null. Please use the upload API to add the model to the Scoring Engine")
      scoreToMap(scoringModel.score(row))
    }).toArray
  }

  def scoreToMap(score: Array[Any]): Map[String, Any] = {
    val outputNames = scoringModel.output().map(o => o.name)
    require(score.length == outputNames.length, "Length of output values should match the output names")
    val outputMap: Map[String, Any] = outputNames.zip(score).map(combined => (combined._1.name, combined._2)).toMap
    outputMap
  }

  /**
   * Store the MAR file locally and load the model saved at the given path
   * @return Model running inside the scoring engine instance
   */
  private def getModel(modelBytes: Array[Byte]): Model = {
    var tempMarFile: File = null
    try{
      tempMarFile = File.createTempFile("model", ".mar")
      FileUtils.writeByteArrayToFile(tempMarFile, modelBytes)
      ModelArchiveFormat.read(tempMarFile, this.getClass.getClassLoader, None)
    }
    finally {
      sys.addShutdownHook(FileUtils.deleteQuietly(tempMarFile)) // Delete temporary File on exit
    }
  }
}

case class ServiceDescription(name: String, identifier: String, versions: List[String])

