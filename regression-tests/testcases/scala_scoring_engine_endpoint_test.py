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

""" test cases for scala scoring engine """
import unittest
import time
import os
from lib import scoring_utils
from sparktkregtests.lib import sparktk_test
from snakebite.client import Client
import ntpath


class ScalaScoring(sparktk_test.SparkTKTestCase):

    def setUp(self):
        """Import the files to test against."""
        super(ScalaScoring, self).setUp()
        schema = [("Vec1", float),
                  ("Vec2", float),
                  ("Vec3", float),
                  ("Vec4", float),
                  ("Vec5", float),
                  ("term", str)]

        self.test_rows = ["Vec1", "Vec2", "Vec3", "Vec4", "Vec5"]
        self.frame_train = self.context.frame.import_csv(
            self.get_file("kmeans_train.csv"), schema=schema)
        self.frame_test = self.context.frame.import_csv(
            self.get_file("kmeans_test.csv"), schema=schema)
        self.hdfs_client = Client("master.organa.cluster.gao", 8020, use_trash=False)

    def test_model_scoring_simple(self):
        scorer =  scoring_utils.scorer("8020")
        
        model = self.context.models.clustering.kmeans.train(self.frame_train, self.test_rows, 5)
        expected_result = model.predict(self.frame_test)
        mar_file = model.export_to_mar(self.get_export_file(self.get_name("kmeans")))
        pandas_rows = expected_result.to_pandas(50)

        for (index, row) in pandas_rows.iterrows():
            result = scorer.score(
                    [dict(zip(self.test_rows, list(row[0:5])))])
            self.assertEqual(row["cluster"], result.json()["data"][0]["score"])

    #def test_model_scoring_bytes(self):
    #    model = self.context.models.clustering.kmeans.train(self.frame_train, self.test_rows, 5)
    #    expected_result = model.predict(self.frame_test)
    #    mar_file = model.export_to_mar(self.get_export_file(self.get_name("kmeans")))
        #mar_file = str(mar_file) + ".mar"
    #    file_name = ntpath.basename(mar_file)
    #    pandas_rows = expected_result.to_pandas(50)
    #    hdfs_path = "sparktk_export/" + str(file_name)
        
    #    cat = subprocess.Popen(["hadoop", "dfs", "-cat", hdfs_path], stdout=subprocess.PIPE)ith open(dest_path) as kmeans_data:
    #        kmeans_bytes = bytearray(kmeans_data.read())

    #def test_display_text(self):
    #    print ""
    #    print ""
    #    print ""
    #    print ""
    #    print "getting file text"
    #    file_contents = self.hdfs_client.ls(["sparktk_export/"])
    #    for x in file_contents:
    #        print x

if __name__ == '__main__':
    unittest.main()
