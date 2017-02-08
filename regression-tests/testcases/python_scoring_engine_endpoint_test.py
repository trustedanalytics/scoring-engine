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
import os
from lib import scoring_utils
from sparktkregtests.lib import sparktk_test


class ScalaScoring(sparktk_test.SparkTKTestCase):

    def setUp(self):
        """Import the files to test against."""
        super(ScalaScoring, self).setUp()
        self.schema = [("data", float),
                       ("name", str)]
        self.train_data = [[2, "ab"], [1, "cd"], [7, "ef"],
                           [1, "gh"], [9, "ij"], [2, "jk"],
                           [0, "mn"], [6, "op"], [5, "qr"]]
        self.test_data = [[0, "ab"], [1, "cd"], [4, "ef"],
                          [3, "gh"], [4, "ij"], [5, "jk"],
                          [10, "mn"], [10, "op"], [2, "qr"]]
        self.frame_train = self.context.frame.create(self.train_data, schema=self.schema)
        self.frame_test = self.context.frame.create(self.test_data, schema=self.schema)
        reg_test_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        self.mar_path = os.path.join(reg_test_root, "datasets") + "/Kmeans.mar"
        self.model = self.context.models.clustering.kmeans.train(self.frame_train, ["data"], 3, seed=5)

    def test_model_scoring_upload_mar_file_simple(self):
        """test creating an empty scoring server then upload mar file and score"""
        expected_res = self.model.predict(self.frame_test)

        files = {"file": open(self.mar_path, 'rb')}

        with scoring_utils.scorer() as scorer:
            scorer.upload_mar_file(files)
            self._score_and_compare_expected_actual_result(expected_res, scorer)

    def test_model_scoring_simple(self):
        """simple model scoring test"""
        expected_result = self.model.predict(self.frame_test)

        with scoring_utils.scorer(self.mar_path) as scorer:
            self._score_and_compare_expected_actual_result(expected_result, scorer)

    def test_model_scoring_send_model_bytes(self):
        """start empty scoring server and test sending model as bytes"""
        expected_res = self.model.predict(self.frame_test)

        with open(self.mar_path, 'rb') as mar_file:
            bytes_data = mar_file.read()

        with scoring_utils.scorer() as scorer:
            scorer.upload_mar_bytes(bytes_data)
            self._score_and_compare_expected_actual_result(expected_res, scorer)

    @unittest.skip("scoring_engine: sending model twice to scoring engine should provide nice error message")
    def test_send_model_twice(self):
        """test to ensure that sending two models fails"""
        with scoring_utils.scorer(self.mar_path) as scorer:
            with scorer.upload_mar_file(self.mar_path):
                response = scorer.score({"data": 2})
                self.assertTrue("500" in str(response))

    def test_score_no_model(self):
        """test scoring on a score server started without a model"""
        with scoring_utils.scorer() as scorer:
            response = scorer.score({"data": 2})
            self.assertTrue("500" in str(response))

    def test_score_invalid_data(self):
        """start a scoring server with valid mar but send invalid data"""
        with scoring_utils.scorer(self.mar_path) as scorer:
            response = scorer.score({"data": "apple"})
            self.assertTrue("500" in str(response))

    def _score_and_compare_expected_actual_result(self, expected, scorer):
        """compare predict and score result"""
        # get pandas frame for ease of access from exp res
        pandas_res = expected.to_pandas()
        # here we will store the equivalent cluster name
        # this is because the cluster names may be labeled differently
        # e.g., predict may call one cluster 0, scoring engine might label it 1
        # so we will record what the equivalent cluster is
        # we only care to ensure that the groups are the same, the labels can differ
        map_cluster_labels = {}

        # iterate through the pandas predict result
        for (index, row) in pandas_res.iterrows():
            score_result = scorer.score([dict(zip(["data"], [row["data"]]))])
            score = score_result.json()["data"][0]["score"]

            # if we have not yet seen this cluster label we add it to our dict
            # of we have already seen this cluster label then we ensure that the
            # mapped cluster is the same
            if row["cluster"] not in map_cluster_labels:
                map_cluster_labels[row["cluster"]] = score
            else:
                self.assertEqual(map_cluster_labels[row["cluster"]], score)


if __name__ == '__main__':
    unittest.main()
