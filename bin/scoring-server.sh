#!/bin/bash
#
#  Copyright (c) 2016 Intel Corporation 
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

#set -o errexit
DIR="$( cd "$( dirname "$0" )" && pwd )"


export MAVEN_REPO=~/.m2/repository
#CP=$DIR/../target/lib/module-loader-master-SNAPSHOT.jar:$DIR/../target/lib/*:$DIR/../target/scoring-engine-1.0-SNAPSHOT.jar:$DIR/../conf/:/etc/hadoop/conf:$MAVEN_REPO/org/scala-lang/scala-library/2.10.5/scala-library-2.10.5.jar:$MAVEN_REPO/com/typesafe/config/1.2.1/config-1.2.1.jar:$MAVEN_REPO/org/scala-lang/scala-reflect/2.10.5/scala-reflect-2.10.5.jar:$MAVEN_REPO/org/trustedanalytics/h2o-models-adapter/1.0-SNAPSHOT
CP=$DIR/../target/lib/*:$DIR/../target/scoring-engine-1.0-SNAPSHOT.jar:/etc/hadoop/conf:$MAVEN_REPO/org/scala-lang/scala-library/2.10.5/scala-library-2.10.5.jar:$MAVEN_REPO/org/scala-lang/scala-reflect/2.10.5/scala-reflect-2.10.5.jar:$MAVEN_REPO/com/typesafe/config/1.2.1/config-1.2.1.jar:$DIR/../conf/:$MAVEN_REPO/org/trustedanalytics/h2o-models-adapter/1.0-SNAPSHOT
#export SEARCH_PATH="-Datk.module-loader.search-path=target/lib:target:scoring-interfaces/target:${HOME}/.m2/"
#export SEARCH_PATH="-Datk.module-loader.search-path=target/lib:target:scoring-interfaces/target:${HOME}/.m2/repository/org/trustedanalytics/h2o-models-adapter/1.0-SNAPSHOT"
pushd $DIR/..
pwd

export HOSTNAME=`hostname`

# Create temporary directory for extracting the model, and add it to the library path
# It is difficult to modify the library path for dynamic libraries after the Java process has started
# LD_LIBRARY_PATH allows the OS to find the dynamic libraries and any dependencies
export MODEL_TMP_DIR=`mktemp -d -t tap-scoring-modelXXXXXXXXXXXXXXXXXX`
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$MODEL_TMP_DIR

# NOTE: Add this parameter to Java for connecting to a debugger
# -agentlib:jdwp=transport=dt_socket,server=n,address=localhost:5005

#CMD=`echo java $@ -Dconfig.trace=loads -XX:MaxPermSize=384m $SEARCH_PATH -Dscoring-engine.tmpdir="$MODEL_TMP_DIR" -cp "$CP" org.trustedanalytics.atk.moduleloader.Module scoring-engine org.trustedanalytics.scoring.ScoringServiceApplication`
CMD=`echo java $@ -Dconfig.trace=loads -XX:MaxPermSize=384m $SEARCH_PATH -Dscoring-engine.tmpdir="$MODEL_TMP_DIR" -cp "$CP" org.trustedanalytics.scoring.MyMainFunction`
echo $CMD
$CMD

popd
