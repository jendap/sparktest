package com.janprach.sparktest.playground

import com.janprach.sparktest.common.SparkTestBase

class MetricsTest extends SparkTestBase {
  import sparkSession.implicits._
  import MetricsTest._

  test("metrics") {
    val records = Seq(Record(1, "foo"), Record(2, "bar"), Record(3, "baz"), Record(4, "baz"))
    records.toDS().createOrReplaceTempView("records")
    val metricsDf = sparkSession.sql("""
SELECT
  COUNT(*) AS num_rows,
  AVG(CAST((value IS NULL) AS int)) AS value_null_ratio
FROM records
        """.trim)
    sparkSession.catalog.dropTempView("records")
    val metrics = Metrics.metricDfToMetricDs[MetricsTest](metricsDf).collect()
    val expectedLabels = Map("class_name" -> classOf[MetricsTest].getSimpleName)
    assert(metrics === Array(Metric("num_rows", 4.0, expectedLabels), Metric("value_null_ratio", 0.0, expectedLabels)))
  }
}

object MetricsTest {
 case class Record(id: Int, value: String)
}
