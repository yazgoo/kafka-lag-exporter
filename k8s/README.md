Install microk8s
```
snap install microk8s --classic
```

Enable addons: `rbac dns registry storage`
- [microk8s - Working with image registries](https://microk8s.io/docs/working)

```
sudo microk8s.enable dns
sudo microk8s.enable rbac
sudo microk8s.enable storage
sudo microk8s.enable registry
```

Add microk8s image registry to local docker daemon config for insecure registries.
Add `localhost:32000` to `/etc/docker/daemon.json` and restart docker.

```
sudo vi /etc/docker/daemon.json
sudo service docker restart
```

### Troubleshooting pod network connectivity...

> The Kubenet network plugin used by MicroK8s creates a cbr0 interface when the first pod is created. If you have ufw enabled, you’ll need to allow traffic on this interface:
> - [microk8s - Troubleshooting](https://microk8s.io/docs/)
>
> ```
> sudo ufw allow in on cbr0 && sudo ufw allow out on cbr0
> ```
> 
> Make sure packets to/from the pod network interface can be forwarded to/from the default interface on the host via the iptables tool. Such changes can be made persistent by installing the iptables-persistent package:
> 
> ```
> sudo iptables -P FORWARD ACCEPT
> sudo apt-get install iptables-persistent
> ```
> 
> or, if using ufw:
> 
> ```
> sudo ufw default allow routed
> ```

Use local `kubectl`

```
microk8s.kubectl config view --raw > $HOME/.kube/microk8s-config
export KUBECONFIG=$HOME/.kube/microk8s-config
```

Install Helm

```
kubectl -n kube-system create serviceaccount tiller
kubectl create clusterrolebinding tiller --clusterrole cluster-admin --serviceaccount=kube-system:tiller
helm init --service-account tiller
```

Install Strimzi

```
helm repo add strimzi http://strimzi.io/charts/
helm install strimzi/strimzi-kafka-operator \
--name strimzi \
--namespace strimzi
```

Create Kafka Cluster

```
kubectl apply -f simple-strimzi.yaml
```


Publish kafka-lag-exporter image to microk8s docker registry

```
export DOCKER_REPOSITORY="localhost:32000"
sbt updateHelmChart docker:publish
```

Install Kafka Lag Exporter

```
helm install ./charts/kafka-lag-exporter \
  --name kafka-lag-exporter \
  --namespace kafka-lag-exporter \
  --set kafkaLagExporterLogLevel=DEBUG \
  --set image.pullPolicy=Always \
  --set watchers.strimzi=true
```

Optionally set a static cluster with additional values in a `values.yaml`

```
clusters:
  - name: "default"
    bootstrapBrokers: "simple-strimzi-kafka-bootstrap.strimzi.svc.cluster.local:9092"
    topicWhitelist:
    - "simplefoo-[a-z]+"
#    - "^xyz-corp-topics\\..+"
    groupWhitelist:
    - "test-group-id"
#    - "^analytics-app-.+"
    # Properties defined by org.apache.kafka.clients.consumer.ConsumerConfig
    # can be defined in this configuration section.
    # https://kafka.apache.org/documentation/#consumerconfigs
#    consumerProperties:
#      security.protocol: SSL
#      ssl.truststore.location: /path/to/my.truststore.jks
#      ssl.trustore.password: mypwd
#    # https://kafka.apache.org/documentation/#adminclientconfigs
#    adminClientProperties:
#      security.protocol: SSL
#      ssl.truststore.location: /path/to/my.truststore.jks
#      ssl.trustore.password: mypwd
    labels:
      location: ny
      zone: "us-east"
```

Run test apps

```
./run.sh
```

Delete test apps

```
./teardown.sh
```

Stop microk8s

```
sudo microk8s.stop
```


TODO:

- create topic
- run producers and consumers with groups

