/*
 * Copyright (C) 2019 Lightbend Inc. <http://www.lightbend.com>
 */

package com.lightbend.kafkalagexporter

import com.lightbend.kafkalagexporter.MetricsSink._
import com.lightbend.kafkalagexporter.PrometheusEndpointSink.{ClusterGlobalLabels, ClusterName, Metrics}
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports
import io.prometheus.client.bridge.Graphite
import io.prometheus.client.{CollectorRegistry, Gauge}

import scala.util.Try

object PrometheusEndpointSink {
  type ClusterName = String
  type GlobalLabels = Map[String, String]
  type ClusterGlobalLabels = Map[ClusterName, GlobalLabels]
  type Metrics = Map[GaugeDefinition, Gauge]

  def apply(definitions: MetricDefinitions, metricWhitelist: List[String], clusterGlobalLabels: ClusterGlobalLabels,
            server: HTTPServer, registry: CollectorRegistry, graphiteConfig: Option[GraphiteConfig]): MetricsSink = {
    Try(new PrometheusEndpointSink(definitions, metricWhitelist, clusterGlobalLabels, server, registry, graphiteConfig))
      .fold(t => throw new Exception("Could not create Prometheus Endpoint", t), sink => sink)
  }
}

class PrometheusEndpointSink private(definitions: MetricDefinitions, metricWhitelist: List[String], clusterGlobalLabels: ClusterGlobalLabels,
                                     server: HTTPServer, registry: CollectorRegistry, graphiteConfig: Option[GraphiteConfig]) extends MetricsSink {
  val clusterOpt = graphiteConfig.map { conf =>
    new Graphite(conf.host, conf.port).start(registry, conf.periodInSeconds)
  }
  DefaultExports.initialize()

  private[kafkalagexporter] val globalLabelNames: List[String] = {
    clusterGlobalLabels.values.flatMap(_.keys).toList.distinct
  }

  private val metrics: Metrics = {
    definitions.filter(d => metricWhitelist.exists(d.name.matches)).map { d =>
      d -> Gauge.build()
        .name(d.name)
        .help(d.help)
        .labelNames(globalLabelNames ++ d.labels: _*)
        .register(registry)
    }.toMap
  }

  override def report(m: MetricValue): Unit = {
    if (metricWhitelist.exists(m.definition.name.matches)) {
      val metric = metrics.getOrElse(m.definition, throw new IllegalArgumentException(s"No metric with definition ${m.definition.name} registered"))
      metric.labels(getGlobalLabelValuesOrDefault(m.clusterName) ++ m.labels: _*).set(m.value)
    }
  }

  override def remove(m: RemoveMetric): Unit = {
    if (metricWhitelist.exists(m.definition.name.matches)) {
      for {
        gauge <- metrics.get(m.definition)
      } {
        val metricLabels = getGlobalLabelValuesOrDefault(m.clusterName) ++ m.labels
        gauge.remove(metricLabels: _*)
      }
    }
  }

  override def stop(): Unit = {
    /*
     * Unregister all collectors (i.e. Gauges).  Useful for integration tests.
     * NOTE: This will nuke all JVM metrics too, but we don't care about those in tests.
     */
    registry.clear()
    server.stop()
    clusterOpt.foreach { cluster => 
      cluster.interrupt()
      cluster.join()
    }
  }


  def getGlobalLabelValuesOrDefault(clusterName: ClusterName): List[String] = {
    val globalLabelValuesForCluster = clusterGlobalLabels.getOrElse(clusterName, Map.empty)
    globalLabelNames.map(l => globalLabelValuesForCluster.getOrElse(l, ""))
  }
}
