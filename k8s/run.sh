#!/usr/bin/env bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
kubectl create namespace strimzi
helm repo add strimzi http://strimzi.io/charts/
helm install strimzi/strimzi-kafka-operator \
--name strimzi \
--namespace strimzi
kubectl apply -f $DIR/simple-strimzi.yaml -n strimzi
kubectl apply -f $DIR/simple-topic.yaml -n strimzi
kubectl apply -f $DIR/consumer.yaml -n strimzi
kubectl apply -f $DIR/producer.yaml -n strimzi