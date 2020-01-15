package com.janprach.sparktest.playground

import org.apache.spark.sql.{DataFrame, Dataset}
import org.apache.spark.sql.functions._

import scala.reflect.runtime.universe._

case class Metric(key: String, value: Double, labels: Map[String, String] = Map.empty[String, String])

object Metrics {
  def metricDfToMetricDs[T: TypeTag](metricsDf: DataFrame): Dataset[Metric] = {
    import metricsDf.sparkSession.implicits._

    val className = typeTag[T].tpe.dealias.typeSymbol.name.toString

    metricsDf
        .select(explode(map(metricsDf.columns.flatMap(c => Array(lit(c), col(c))):_*)))
        .withColumn("labels", map(lit("class_name"), lit(className)))
        .as[Metric]
  }
}
