#!/usr/bin/env bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
kubectl delete -f $DIR/producer.yaml -n strimzi
kubectl delete -f $DIR/consumer.yaml -n strimzi
kubectl delete -f $DIR/simple-topic.yaml -n strimzi
kubectl delete -f $DIR/simple-strimzi.yaml -n strimzi
helm delete strimzi --purge
kubectl delete pvc -l strimzi.io/cluster=simple-strimzi -n strimzi
