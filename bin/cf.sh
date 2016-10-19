#!/bin/bash

echo "Starting model scoring startup script"
env
#set -o errexit
DIR="$( cd "$( dirname "$0" )" && pwd )"

export ATK_CONF_DIR="$DIR/../conf"
export YARN_CONF_DIR=$ATK_CONF_DIR

echo "DIR: $DIR"

SCORING_JAR=$(find `pwd` -name "model-scoring*.jar" )
echo "SCORING_JAR" $SCORING_JAR
CP=$SCORING_JAR:$DIR/../lib/*:$DIR/../conf/logback.xml:$DIR/../conf

#export SEARCH_PATH="-Datk.module-loader.search-path=$DIR/../lib/"

jq=$DIR/../jq
echo "make jq executable"
chmod +x $jq

pwd 

echo START Parse hadoop zip
pushd $DIR/../conf

rm -rf hadoop.zip

rm -rf hdfs-size.xml

echo "LOOK at vcap"

echo $VCAP_SERVICES | $jq -rc '.hdfs[0].credentials.HADOOP_CONFIG_ZIP.encoded_zip' | grep -v null | base64 -d > hadoop.zip && unzip hadoop.zip
ls -la

mv hadoop-conf/* `pwd`

pwd

popd
echo END Parse hadoop zip


pushd $DIR/..
pwd
export PWD=`pwd`

export PATH=$PWD/.java-buildpack/open_jdk_jre/bin:$PATH
export JAVA_HOME=$PWD/.java-buildpack/open_jdk_jre


# Create temporary directory for extracting the model, and add it to the library path
# It is difficult to modify the library path for dynamic libraries after the Java process has started
# LD_LIBRARY_PATH allows the OS to find the dynamic libraries and any dependencies
export MODEL_TMP_DIR=`mktemp -d -t tap-scoring-modelXXXXXXXXXXXXXXXXXX`
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$MODEL_TMP_DIR
export MODEL_SCORING_MAIN="org.trustedanalytics.scoring.MyMainFunction"

echo java $@  $SEARCH_PATH -Datk.scoring-engine.tmpdir="$MODEL_TMP_DIR" -cp  "$CP"  $MODEL_SCORING_MAIN
     java $@  $SEARCH_PATH -Datk.scoring-engine.tmpdir="$MODEL_TMP_DIR" -cp "$CP"  $MODEL_SCORING_MAIN

popd
