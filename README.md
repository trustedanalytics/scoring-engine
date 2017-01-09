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
