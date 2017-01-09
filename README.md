#Scoring Engine

The Scoring Engine is a REST server capable of loading trained machine learning models exported by Spark-tk in MAR (Model ARchive) format and using the models to score streams of incoming data. These models implement Model ARchive Interface defined in the ModelArchiver repository at: https://github.com/trustedanalytics/ModelArchiver. Applications can use the Scoring Engine RESTful API to get predictions produced by a model.

##scoring-pipelines vs. scoring-engine

If you need to perform transformations on the incoming data you wish to score, use the scoring-pipelines instead of the scoring-engine. The scoring-pipelines perform supported data transformations and automatically submit the output to the scoring engine. The repo for the scoring-pipelines is https://github.com/trustedanalytics/scoring-pipelines.


#Creating a scoring engine instance

>These steps assume you already have a model in MAR format and have the URI to that model.  

You can create a scoring engine instance from the TAP Console, as follows:  

1. Navigate to **Services > Marketplace**.  

9. Scroll down to find the **Scoring Engine for Spark-tk** and select it.  

9. Enter a name for your instance in the **Instance Name* field.  

9. Select **+ Add an extra parameter**.  

9. Fill in two values: key = **uri**; value = the URI of the model you wish to use.  

9. Click the **Create new instance** button.  

>This may take a minute or two.  

When done, you can see your scoring engine listed on the **Applications** page.  

#Scoring Example  

The sample below is a Python script to send requests to the scoring engine containing a trained Random Forest Classifier model:  

```
>>> frame = tc.frame.create([[1,19.8446136104,2.2985856384],[1,16.8973559126,2.6933495054],
...                                 [1,5.5548729596,2.7777687995],[0,46.1810010826,3.1611961917],
...                                 [0,44.3117586448,3.3458963222],[0,34.6334526911,3.6429838715]],
...                                 [('Class', int), ('Dim_1', float), ('Dim_2', float)])

>>> frame.inspect()
[#]  Class  Dim_1          Dim_2
=======================================
[0]      1  19.8446136104  2.2985856384
[1]      1  16.8973559126  2.6933495054
[2]      1   5.5548729596  2.7777687995
[3]      0  46.1810010826  3.1611961917
[4]      0  44.3117586448  3.3458963222
[5]      0  34.6334526911  3.6429838715

>>> model = tc.models.classification.random_forest_classifier.train(frame,
...                                                                ['Dim_1', 'Dim_2'],
...                                                                'Class',
...                                                                num_classes=2,
...                                                                num_trees=1,
...                                                                impurity="entropy",
...                                                                max_depth=4,
...                                                                max_bins=100)

>>> model.feature_importances()
{u'Dim_1': 1.0, u'Dim_2': 0.0}

>>> predicted_frame = model.predict(frame, ['Dim_1', 'Dim_2'])

>>> predicted_frame.inspect()
[#]  Class  Dim_1          Dim_2         predicted_class
========================================================
[0]      1  19.8446136104  2.2985856384                1
[1]      1  16.8973559126  2.6933495054                1
[2]      1   5.5548729596  2.7777687995                1
[3]      0  46.1810010826  3.1611961917                0
[4]      0  44.3117586448  3.3458963222                0
[5]      0  34.6334526911  3.6429838715                0

>>> test_metrics = model.test(frame, ['Dim_1','Dim_2'], 'Class')

>>> test_metrics
accuracy         = 1.0
confusion_matrix =             Predicted_Pos  Predicted_Neg
Actual_Pos              3              0
Actual_Neg              0              3
f_measure        = 1.0
precision        = 1.0
recall           = 1.0

>>> model.save("sandbox/randomforestclassifier")

>>> restored = tc.load("sandbox/randomforestclassifier")

>>> restored.label_column == model.label_column
True

>>> restored.seed == model.seed
True

>>> set(restored.observation_columns) == set(model.observation_columns)
True
```  

The trained model can also be exported to a .mar file, to be used with the scoring engine:  
```  
>>> canonical_path = model.export_to_mar("sandbox/rfClassifier.mar")
```  

