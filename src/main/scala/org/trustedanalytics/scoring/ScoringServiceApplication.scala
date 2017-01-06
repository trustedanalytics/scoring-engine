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

import java.util.{ArrayList => JArrayList}

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import org.trustedanalytics.scoring.interfaces.Model
import spray.can.Http

import scala.concurrent.duration._

/**
 * Scoring Service Application - a REST application used by client layer to communicate with the Model.
 *
 * See the 'scoring_server.sh' to see how the launcher starts the application.
 */
class ScoringServiceApplication {
  private val logger = LoggerFactory.getLogger(this.getClass)
  val config = ConfigFactory.load(this.getClass.getClassLoader)

  /**
   * Main entry point to start the Scoring Service Application
   */
  def start() = {
      val model = getModel
      val service = new ScoringService(model)
      createActorSystemAndBindToHttp(service)
  }

  /**
   * Stop this component
   */
  def stop(): Unit = {
  }

  /**
   * load the model saved at the given path
   * @return Model running inside the scoring engine instance
   */
  private def getModel(): Model = {
    val marFilePath = config.getString("trustedanalytics.scoring-engine.archive-mar")
    ScoringEngineHelper.getModel(marFilePath)
  }



  /**
   * We need an ActorSystem to host our application in and to bind it to an HTTP port
   */
  private def createActorSystemAndBindToHttp(scoringService: ScoringService): Unit = {
    try {
      // create the system
      implicit val system = ActorSystem("trustedanalytcs-scoring")
      implicit val timeout = Timeout(7.seconds)
      println("created actor system")
      val service = system.actorOf(Props(new ScoringServiceActor(scoringService)), "scoring-service")
      println("service created")
      // Bind the Spray Actor to an HTTP Port
      // start a new HTTP server with our service actor as the handler
      IO(Http) ? Http.Bind(service, interface = config.getString("trustedanalytics.scoring.host"), port = config.getInt("trustedanalytics.scoring.port"))
      logger.info("Scoring server is running now")
    }
    catch{
      case t: Throwable =>
        t.printStackTrace()
      }
  }


}

