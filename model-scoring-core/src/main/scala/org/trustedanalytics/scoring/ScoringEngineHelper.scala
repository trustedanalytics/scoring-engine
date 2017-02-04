/**
  *  Copyright (c) 2015 Intel Corporation
  *
  *  Licensed under the Apache License, Version 2.0 (the "License");
  *  you may not use this file except in compliance with the License.
  *  You may obtain a copy of the License at
  *
  *       http://www.apache.org/licenses/LICENSE-2.0
  *
  *  Unless required by applicable law or agreed to in writing, software
  *  distributed under the License is distributed on an "AS IS" BASIS,
  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  *  See the License for the specific language governing permissions and
  *  limitations under the License.
  */

package org.trustedanalytics.scoring

import java.io.File
import java.net.URI
import java.util.{ArrayList => JArrayList}

import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.slf4j.LoggerFactory
import org.trustedanalytics.model.archive.format.ModelArchiveFormat
import org.trustedanalytics.scoring.interfaces.Model

object ScoringEngineHelper {
  private val logger = LoggerFactory.getLogger(this.getClass)
  val config = ConfigFactory.load(this.getClass.getClassLoader)

  /**
    *
    * @param model        Original model
    * @param revisedModel Revised model
    * @return true if model compatible i.e model type is same and model input/output parameters are same
    *         else returns false
    */
  def isModelCompatible(model: Model, revisedModel: Model): Boolean = {
    model.modelMetadata().modelType == revisedModel.modelMetadata().modelType &&
      model.input().deep == revisedModel.input().deep &&
      model.output().deep == revisedModel.output().deep
  }

  private def getAWSConfig(): Configuration = {
    val proxyHost = config.getString("trustedanalytics.aws.fs.s3a.proxy.host")
    val proxyPort  = config.getString("trustedanalytics.aws.fs.s3a.proxy.port")
    val accessKey = config.getString("trustedanalytics.aws.fs.s3a.access.key")
    val secretKey = config.getString("trustedanalytics.aws.fs.s3a.secret.key")

    val cfg =  new Configuration()
    if(proxyHost != "" && proxyPort != "") {
      cfg.set("fs.s3a.proxy.host",proxyHost)
      cfg.set("fs.s3a.proxy.port",proxyPort)
    }
    if(accessKey != "") cfg.set("fs.s3a.access.key",accessKey) else throw new Exception("Configuration do not have AWS access key")
    if(secretKey != "") cfg.set("fs.s3a.secret.key",secretKey) else throw new Exception("Configuration do not have AWS secret key")
    cfg
  }
  def getModel(marFilePath: String): Model = {
    if (marFilePath != "") {
      try {
        logger.info("Calling ModelArchiveFormat.read() to load the model stored locally")
        return ModelArchiveFormat.read(new File(marFilePath), this.getClass.getClassLoader, None)
      } catch {
        case e: Exception =>
          logger.info("Unale to load model from local filesystem, trying to load the model from HDFS")
          var tempMarFile: File = null
          tempMarFile = File.createTempFile("model", ".mar")
          try
          {
            val cfg = getAWSConfig()
            val hdfsFileSystem = org.apache.hadoop.fs.FileSystem.get(new URI(marFilePath), cfg)
            hdfsFileSystem.copyToLocalFile(false, new Path(marFilePath), new Path(tempMarFile.getAbsolutePath))
            val hdfsMarFilePath = tempMarFile.getAbsolutePath
            sys.addShutdownHook(FileUtils.deleteQuietly(tempMarFile)) // Delete temporary directory on exit
            return ModelArchiveFormat.read(new File(hdfsMarFilePath), this.getClass.getClassLoader, None)
          } catch {
            case e: Exception =>
              logger.info("Unale to load model from HDFS...\n"+e.getMessage)
              logger.info("\n"+e.getStackTraceString)
              return null
          } finally {
            FileUtils.deleteQuietly(tempMarFile)
          }
      }
    } else {
      return null
    }
  }
}