FROM clearlinux:latest

ARG HOME=/home/modelscoring
ARG MODEL_SCORING_PACKAGE

RUN useradd modelscoring && swupd bundle-add java-basic sysadmin-basic && java -version && javac -version

USER modelscoring
WORKDIR /home/modelscoring/
ADD $MODEL_SCORING_PACKAGE .

WORKDIR /home/modelscoring/
ENTRYPOINT [ "bin/model-scoring.sh" ]

