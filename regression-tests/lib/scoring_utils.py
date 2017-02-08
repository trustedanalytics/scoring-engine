# vim: set encoding=utf-8

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

""" Library to support scoring in TAP for the ATK service """
import subprocess as sp
import requests
import time
import signal
import os
import config


class scorer(object):

    def __init__(self, hdfs_path=None, port=config.port, pipeline=False, host=config.scoring_engine_host):
        """Set up the server location, port and model file"""
        self.name = host.split('.')[0]
        self.host = host
        self.pipeline = pipeline
        self.port = port
        self.scoring_process = None
        self.hdfs_path = hdfs_path
        self.full_host_url = "http://" + str(self.host) + ":" + str(self.port)

    def __enter__(self):
        """Activate the Server"""
        # change current working directory to point at scoring_engine dir
        run_path = os.path.abspath(os.path.join(config.root, "model-scoring-core"))

        # keep track of cwd for future
        test_dir = os.getcwd()
        os.chdir(run_path)

        # make a new process group
        if self.hdfs_path:
            self.scoring_process = sp.Popen(["./bin/scoring-server.sh",
                                             "-Dtrustedanalytics.scoring-engine.archive-mar=%s" % self.hdfs_path,
                                             "-Dtrustedanalytics.scoring.host=%s" % self.host,
                                             "-Dtrustedanalytics.scoring.port=%s" % self.port], preexec_fn=os.setsid)
        else:
            self.scoring_process = sp.Popen(["./bin/scoring-server.sh",
                                             "-Dtrustedanalytics.scoring.host=%s" % self.host,
                                             "-Dtrustedanalytics.scoring.port=%s" % self.port], preexec_fn=os.setsid)

        # restore cwd
        os.chdir(test_dir)

        # wait for server to start
        time.sleep(20)

        return self

    def __exit__(self, *args):
        """Teardown the server"""
        # Get the process group to kill all of the suprocesses
        pgrp = os.getpgid(self.scoring_process.pid)
        os.killpg(pgrp, signal.SIGKILL)
        time.sleep(50)

    def upload_mar_bytes(self, file_bytes):
        """gives mar file to empty scoring server as bytes data"""
        requests.post(url=self.full_host_url + "/uploadMarBytes",
                      data=file_bytes,
                      headers={"Content-type": "application/octet-stream"})

    def upload_mar_file(self, files):
        """gives a mar file to a an empty scoring server"""
        requests.post(url=self.full_host_url + "/uploadMarFile", files=files)

    def score(self, data_val):
        """score the json set data_val"""

        # Magic headers to make the server respond appropriately
        # Ask the head of scoring why these
        headers = {'Content-type': 'application/json',
                   'Accept': 'application/json,text/plain'}

        response = requests.post(
                self.full_host_url + "/v2/score", json={"records": data_val}, headers=headers)

        return response
